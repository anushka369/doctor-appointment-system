package com.clinic.config;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.SQLException;

/**
 * Opens an H2 TCP server inside the backend so a database tool (for example a VS Code extension)
 * can connect to the same file database while the app is running. Local connections only, and
 * -ifExists stops clients from creating new databases.
 * Client URL: jdbc:h2:tcp://localhost:9092/<absolute path to backend/data/clinic>;MODE=MySQL;DATABASE_TO_LOWER=TRUE
 */
@Configuration
@Profile("!mysql")
@ConditionalOnProperty(name = "clinic.h2.tcp.enabled", havingValue = "true")
public class H2TcpServer {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server tcpServer(@Value("${clinic.h2.tcp.port:9092}") String port) throws SQLException {
        return Server.createTcpServer("-tcpPort", port, "-ifExists");
    }
}
