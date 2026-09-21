package com.generationb.foundation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Refuses every request from an account that is still on a temporary password, except the few
 * needed to get off it.
 *
 * <p>This exists because the alternative — the frontend noticing a flag and routing to a
 * change-password screen — is not a control. The login returns a working access token; anyone
 * who wanted to skip the prompt could take that token and call the API directly. The seeded
 * accounts' password is in the README, so "the UI asks nicely" is not enough.
 *
 * <p>Ordered after {@link JwtAuthenticationFilter}, which resolves the flag from the token and
 * leaves it on the request. Anonymous requests pass straight through: they are the entry
 * point's problem, not this one's.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeGate extends OncePerRequestFilter {

    /**
     * The minimum surface a user on a temporary password needs.
     *
     * <p>{@code /api/auth/refresh} is here on purpose: leaving it out would expire the session
     * after the access-token window while the user was still choosing a password, and drop them
     * back at a login screen that hands them the same wall again.
     */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/change-password",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/auth/me",
            "/api/auth/password-policy"
    );

    static final String ERROR_CODE = "PASSWORD_CHANGE_REQUIRED";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isBlocked(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ERROR_CODE,
                "Set a new password before continuing.",
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                request.getRequestURI(),
                null));
    }

    private boolean isBlocked(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.MUST_CHANGE_PASSWORD_ATTRIBUTE))) {
            return false;
        }
        // Preflight carries no credentials and must never be answered with an error body.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return !ALLOWED_PATHS.contains(path) && !path.startsWith("/api/health");
    }
}
