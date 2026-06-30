package com.vink.backend.middleware;

import com.vink.backend.model.AuthPayload;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Java port of server/src/middleware/auth.ts (requireAuth).
 * Validates the Bearer JWT on every request and, if valid, stores the
 * decoded AuthPayload as a request attribute ("authUser") for controllers
 * to read - equivalent to Express's req.user.
 *
 * Public/auth-exempt paths are configured in SecurityConfig.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${vink.jwt.secret:vink-mvno-dev-secret-change-in-prod}")
    private String jwtSecret;

    public static final String REQUEST_ATTR_USER = "authUser";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            // No token: let request pass through. Controllers/endpoints that
            // require auth check the "authUser" attribute themselves (or are
            // protected via SecurityConfig) - mirroring requireAuth() being
            // applied per-route in the original Express app.
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(
                    jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32
                            ? jwtSecret.getBytes(StandardCharsets.UTF_8)
                            : pad(jwtSecret));

            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            AuthPayload user = new AuthPayload(
                    claims.getSubject(),
                    claims.get("email", String.class),
                    claims.get("role", String.class));

            request.setAttribute(REQUEST_ATTR_USER, user);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Token expired or invalid\"}");
        }
    }

    private byte[] pad(String secret) {
        // dev-only convenience so short dev secrets still satisfy HS256's 256-bit key requirement
        StringBuilder sb = new StringBuilder(secret);
        while (sb.length() < 32) sb.append(secret);
        return sb.substring(0, 32).getBytes(StandardCharsets.UTF_8);
    }
}
