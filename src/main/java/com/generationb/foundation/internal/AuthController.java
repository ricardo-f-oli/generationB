package com.generationb.foundation.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.ApiResponse;
import com.generationb.foundation.BrandContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Q-B6: the refresh token is now also written as an HttpOnly cookie so XSS cannot read it.
     * It is still returned in the body during the transition, because the frontend is served
     * from a different site (Vercel) to the API (Render) and third-party cookies are blocked by
     * default in Safari. Removing the body field is a one-line change once both sit behind the
     * same domain — tracked in QUESTIONS.md.
     */
    private static final String REFRESH_COOKIE = "genb_refresh";

    private final AuthService authService;

    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    public record LoginRequest(
            String identifier,
            String email,
            String username,
            @NotBlank(message = "Password is required") String password) {

        String resolvedIdentifier() {
            if (identifier != null && !identifier.isBlank()) return identifier;
            if (email != null && !email.isBlank()) return email;
            return username;
        }
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Token is required") String token,
            @NotBlank(message = "New password is required") String newPassword) {
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        AuthService.AuthResult result =
                authService.login(request.resolvedIdentifier(), request.password(), clientIp(httpRequest));
        writeRefreshCookie(httpResponse, result.refreshToken());
        return ApiResponse.of(payload(result));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody(required = false) RefreshRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        String token = readRefreshCookie(httpRequest);
        if (token == null && request != null) {
            token = request.refreshToken();
        }
        AuthService.AuthResult result = authService.refresh(token);
        writeRefreshCookie(httpResponse, result.refreshToken());
        return ApiResponse.of(payload(result));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(@RequestBody(required = false) RefreshRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        String token = readRefreshCookie(httpRequest);
        if (token == null && request != null) {
            token = request.refreshToken();
        }
        authService.logout(token);
        clearRefreshCookie(httpResponse);
        return ApiResponse.of(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Always the same response, whether or not the address exists.
        return ApiResponse.of(Map.of(
                "message", "If that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.of(Map.of("message", "Password updated successfully"));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        UUID currentUserId = BrandContext.getCurrentUserId();
        if (currentUserId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return ApiResponse.of(authService.getMe(currentUserId));
    }

    /** Exposed so the frontend can show the rule before the user submits. */
    @GetMapping("/password-policy")
    public ApiResponse<Map<String, Object>> passwordPolicy() {
        return ApiResponse.of(Map.of(
                "minLength", PasswordPolicy.MIN_LENGTH,
                "description", "At least " + PasswordPolicy.MIN_LENGTH
                        + " characters. No other restrictions — a memorable passphrase is ideal."));
    }

    // ------------------------------------------------------------- helpers

    private Map<String, Object> payload(AuthService.AuthResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", result.accessToken());
        body.put("refreshToken", result.refreshToken());
        body.put("user", result.user());
        return body;
    }

    private void writeRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
