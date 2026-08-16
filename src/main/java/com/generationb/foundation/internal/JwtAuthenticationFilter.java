package com.generationb.foundation.internal;

import com.generationb.foundation.BrandContext;
import com.generationb.foundation.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("requestId", correlationId);
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            BrandContext.clear();
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }

    /**
     * Q-A5: {@code extractClaims} used to run outside any try/catch, so an expired token escaped
     * the filter as a 500. An unreadable token now simply leaves the request unauthenticated, and
     * the entry point turns that into a 401 — which is what the frontend's refresh flow keys on.
     */
    private void authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.extractClaims(token);
            String email = claims.getSubject();
            String brandIdStr = claims.get("brand_id", String.class);
            String userIdStr = claims.get("user_id", String.class);
            String roleStr = claims.get("role", String.class);

            if (email == null || brandIdStr == null || roleStr == null) {
                return;
            }

            Role role = Role.fromString(roleStr);
            if (role == null) {
                log.warn("Rejecting token carrying unknown role '{}'", roleStr);
                return;
            }

            UUID brandId = UUID.fromString(brandIdStr);
            UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;

            BrandContext.set(brandId, userId, role.name());
            MDC.put("brandId", brandId.toString());
            if (userId != null) {
                MDC.put("userId", userId.toString());
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    email, null, Collections.singletonList(new SimpleGrantedAuthority(role.authority()))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | IllegalArgumentException ex) {
            // Expired, malformed or tampered token: stay anonymous, let the entry point answer 401.
            log.debug("Rejected bearer token: {}", ex.getMessage());
        }
    }
}
