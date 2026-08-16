package com.generationb.foundation.internal;

import com.generationb.foundation.*;
import com.generationb.foundation.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Q-B10: lockout thresholds. */
    private static final int MAX_FAILURES_PER_IDENTIFIER = 5;
    private static final int MAX_FAILURES_PER_IP = 20;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    /** Q-B8: cap on password-reset emails per user per hour. */
    private static final int MAX_RESETS_PER_HOUR = 3;

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailSender emailSender;

    // ---------------------------------------------------------------- login

    public record AuthResult(String accessToken, String refreshToken, Map<String, Object> user) {
    }

    @Transactional
    public AuthResult login(String identifier, String password, String ipAddress) {
        if (identifier == null || identifier.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Email/username and password are required");
        }

        Instant windowStart = Instant.now().minus(FAILURE_WINDOW);
        if (loginAttemptRepository.countRecentFailuresForIdentifier(identifier, windowStart) >= MAX_FAILURES_PER_IDENTIFIER) {
            throw ApiException.tooManyRequests(
                    "Too many failed attempts. Please try again in " + LOCKOUT_DURATION.toMinutes() + " minutes.");
        }
        if (ipAddress != null
                && loginAttemptRepository.countRecentFailuresForIp(ipAddress, windowStart) >= MAX_FAILURES_PER_IP) {
            throw ApiException.tooManyRequests("Too many failed attempts from this network.");
        }

        Optional<User> maybeUser = userRepository.findByIdentifier(identifier);

        // Q-B10: "account disabled" and "wrong password" return the same message so the endpoint
        // cannot be used to enumerate accounts. The distinction is logged, not returned.
        if (maybeUser.isEmpty()) {
            recordFailure(identifier, ipAddress);
            throw ApiException.unauthorized("Invalid credentials");
        }

        User user = maybeUser.get();

        if (user.isLocked()) {
            recordFailure(identifier, ipAddress);
            throw ApiException.tooManyRequests("This account is temporarily locked. Please try again shortly.");
        }
        if (!user.isActive()) {
            log.info("Login attempt against disabled account userId={}", user.getId());
            recordFailure(identifier, ipAddress);
            throw ApiException.unauthorized("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            registerFailedPassword(user);
            recordFailure(identifier, ipAddress);
            throw ApiException.unauthorized("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        loginAttemptRepository.save(LoginAttempt.of(identifier, ipAddress, true));

        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getBrandId(), user.getId(), user.getRole());
        String rawRefreshToken = issueRefreshToken(user);

        return new AuthResult(accessToken, rawRefreshToken, toUserMap(user));
    }

    private void registerFailedPassword(User user) {
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= MAX_FAILURES_PER_IDENTIFIER) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            log.warn("Locking account userId={} after {} failed attempts", user.getId(), failures);
        }
        userRepository.save(user);
    }

    private void recordFailure(String identifier, String ipAddress) {
        loginAttemptRepository.save(LoginAttempt.of(identifier, ipAddress, false));
    }

    // -------------------------------------------------------------- refresh

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token is required");
        }

        // Q-B7: one indexed lookup instead of scanning every row with BCrypt.
        RefreshToken stored = refreshTokenRepository
                .findByTokenDigest(TokenHasher.digest(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));

        if (!stored.isUsable()) {
            // Reuse of a revoked token is the signature of a stolen token: drop the whole family.
            if (stored.getRevokedAt() != null) {
                log.warn("Refresh token reuse detected for userId={}; revoking all sessions",
                        stored.getUser().getId());
                refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), Instant.now());
            }
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getBrandId(), user.getId(), user.getRole());
        String newRefreshToken = issueRefreshToken(user);

        return new AuthResult(accessToken, newRefreshToken, toUserMap(user));
    }

    private String issueRefreshToken(User user) {
        String raw = TokenHasher.generateToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenDigest(TokenHasher.digest(raw));
        // token_hash is NOT NULL in the schema; store the digest there too rather than a
        // second (slow, useless) BCrypt hash.
        token.setTokenHash(token.getTokenDigest());
        token.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL));
        refreshTokenRepository.save(token);
        return raw;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenDigest(TokenHasher.digest(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    // ------------------------------------------------------- password reset

    @Transactional
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            long recent = passwordResetTokenRepository.countRecentForUser(
                    user.getId(), Instant.now().minus(Duration.ofHours(1)));
            if (recent >= MAX_RESETS_PER_HOUR) {
                // Silently stop: telling the caller would leak that the address exists.
                log.info("Password reset rate limit reached for userId={}", user.getId());
                return;
            }

            String rawToken = TokenHasher.generateToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenDigest(TokenHasher.digest(rawToken));
            resetToken.setTokenHash(resetToken.getTokenDigest());
            resetToken.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            passwordResetTokenRepository.save(resetToken);

            String recipient = user.getEmail();
            // Q-E11: the email leaves the transaction, so a slow provider cannot hold a DB
            // connection and a rollback cannot "unsend" a message.
            afterCommit(() -> emailSender.sendPasswordResetEmail(recipient, rawToken));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("Reset token is required");
        }
        PasswordPolicy.validate(newPassword);

        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenDigest(TokenHasher.digest(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset token"));

        if (!stored.isUsable()) {
            throw ApiException.badRequest("Invalid or expired reset token");
        }

        User user = stored.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        stored.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(stored);

        // Changing a password ends every existing session.
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), Instant.now());
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    // ------------------------------------------------------------------ me

    @Transactional(readOnly = true)
    public Map<String, Object> getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Session is no longer valid"));
        return toUserMap(user);
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("name", user.getName() != null ? user.getName() : user.getEmail());
        map.put("role", user.getRole());
        map.put("brandId", user.getBrandId());
        return map;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
