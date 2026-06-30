package com.vink.backend.middleware;

import com.vink.backend.model.AuthPayload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Java port of the requireAuth / requireRole helpers from
 * server/src/middleware/auth.ts. Controllers call AuthGuard.requireAuth(req)
 * at the top of a handler (mirroring the Express middleware chain), and
 * AuthGuard.requireRole(user, "superadmin", ...) where the original route
 * declared requireRole(...).
 */
public final class AuthGuard {

    private AuthGuard() {}

    public static AuthPayload requireAuth(HttpServletRequest request) {
        Object attr = request.getAttribute(JwtAuthFilter.REQUEST_ATTR_USER);
        if (attr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        return (AuthPayload) attr;
    }

    public static void requireRole(AuthPayload user, String... roles) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!Set.of(roles).contains(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient privileges");
        }
    }
}
