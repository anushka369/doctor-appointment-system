package com.clinic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Keeps one CSV file per table in sync with the database so the data can be viewed in an editor.
 * The files are read-only copies: editing them does not change the database.
 * Exports on startup and after every successful POST/PUT/PATCH/DELETE under /api.
 */
@Component
public class CsvExporter implements ApplicationRunner, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CsvExporter.class);
    private static final List<String> TABLES = List.of("doctors", "patients", "appointments");

    private final DataSource dataSource;
    private final Path dir;
    private final boolean enabled;

    public CsvExporter(DataSource dataSource,
                       @Value("${clinic.csv.dir:./data}") String dir,
                       @Value("${clinic.csv.enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.dir = Path.of(dir);
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        exportAll();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
                boolean write = !List.of("GET", "HEAD", "OPTIONS").contains(req.getMethod());
                boolean auth = req.getRequestURI().startsWith("/api/auth/");
                if (write && !auth && ex == null && res.getStatus() < 400) {
                    exportAll();
                }
            }
        }).addPathPatterns("/api/**");
    }

    public synchronized void exportAll() {
        if (!enabled) return;
        try {
            Files.createDirectories(dir);
            for (String table : TABLES) {
                export(table);
            }
        } catch (IOException | SQLException e) {
            // Never fail a request because the CSV copy could not be written.
            log.warn("CSV export failed: {}", e.getMessage());
        }
    }

    private void export(String table) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            for (int i = 1; i <= n; i++) {
                sb.append(i > 1 ? "," : "").append(cell(md.getColumnLabel(i).toLowerCase()));
            }
            sb.append('\n');
            while (rs.next()) {
                for (int i = 1; i <= n; i++) {
                    Object v = rs.getObject(i);
                    sb.append(i > 1 ? "," : "").append(cell(v == null ? "" : v.toString()));
                }
                sb.append('\n');
            }
        }
        // Write to a temp file then move, so an editor never sees a half-written CSV.
        Path tmp = dir.resolve(table + ".csv.tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, dir.resolve(table + ".csv"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String cell(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
