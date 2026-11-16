package com.clinic.auth;

import com.clinic.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Requires a valid Bearer token on every /api call (except the login endpoints) and checks role access. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "session";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equals(req.getMethod())) {
            return true; // CORS preflight
        }
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Please sign in");
        }
        Session session = auth.session(header.substring(7).trim())
                .orElseThrow(() -> ApiException.unauthorized("Your session has expired. Please sign in again."));
        String path = req.getRequestURI().replaceFirst("^/api", "");
        if (!allowed(session.role(), req.getMethod(), path)) {
            throw ApiException.forbidden("Your role is not allowed to do that");
        }
        req.setAttribute(ATTR, session);
        return true;
    }

    /** Coarse role rules. Row-level ownership (own appointments only) is enforced in AppointmentController. */
    static boolean allowed(String role, String method, String path) {
        boolean admin = Roles.ADMIN.equals(role);
        if (path.startsWith("/auth/")) {
            return true;
        }
        if (path.equals("/profile")) {
            return true;
        }
        if (path.startsWith("/patients")) {
            return admin;
        }
        if (path.startsWith("/doctors")) {
            return "GET".equals(method) || admin;
        }
        if (path.startsWith("/appointments")) {
            if ("DELETE".equals(method)) {
                return admin;
            }
            if ("POST".equals(method)) {
                return admin || Roles.PATIENT.equals(role);
            }
            return true;
        }
        return false;
    }
}
