package com.generationb.foundation.internal;

import com.generationb.foundation.PasswordResetTokenRepository;
import com.generationb.foundation.RefreshTokenRepository;
import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Requirement #37: the security exhaust — login attempts and spent tokens.
 *
 * <p>Login attempts hold an email address and an IP, which is personal data even when the login
 * failed. They earn their keep for as long as somebody might investigate a break-in attempt and
 * no longer; 90 days is the usual window and covers a quarterly security review.
 *
 * <p>Expired and revoked tokens are dead weight with a user id attached. They are removed a short
 * grace period after expiry, which keeps the unique-digest indexes small as well.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthDataRetention implements RetentionSweeper {

    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${retention.login-attempts.days:90}")
    private int loginAttemptDays;

    /** A grace period past expiry, so a user retrying a just-expired link still gets told why. */
    @Value("${retention.expired-tokens.days:7}")
    private int expiredTokenDays;

    @Override
    public String dataset() {
        return "Login attempts and expired tokens";
    }

    @Override
    public String policy() {
        return "Login attempts deleted after " + loginAttemptDays + " days. Password reset and "
                + "refresh tokens deleted " + expiredTokenDays + " days after they expire.";
    }

    @Override
    @Transactional
    public Outcome sweep(boolean dryRun) {
        Instant attemptsBefore = Instant.now().minus(Duration.ofDays(loginAttemptDays));
        Instant tokensBefore = Instant.now().minus(Duration.ofDays(expiredTokenDays));

        int affected;
        if (dryRun) {
            affected = loginAttemptRepository.countBefore(attemptsBefore)
                    + passwordResetTokenRepository.countExpiredBefore(tokensBefore)
                    + refreshTokenRepository.countExpiredBefore(tokensBefore);
        } else {
            affected = loginAttemptRepository.deleteBefore(attemptsBefore)
                    + passwordResetTokenRepository.deleteExpiredBefore(tokensBefore)
                    + refreshTokenRepository.deleteExpiredBefore(tokensBefore);
            if (affected > 0) {
                log.info("Retention: deleted {} login attempt(s) and spent token(s)", affected);
            }
        }
        return Outcome.of(dataset(), policy(), affected);
    }
}
