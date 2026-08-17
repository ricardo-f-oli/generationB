package com.generationb.foundation.internal;

import com.generationb.foundation.*;
import com.generationb.foundation.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #35: user administration — who can get in, with what role.
 *
 * <p>Everything here is admin-only. Two rules are enforced rather than left to good manners: an
 * admin cannot deactivate or demote themselves (which would lock the brand out of its own
 * settings), and a brand must keep at least one active admin.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailSender emailSender;

    public record UserRow(
            UUID id, String name, String email, String username, String role,
            boolean active, boolean locked, Instant lastLoginAt, Instant createdAt) {
    }

    public record CreateUserCommand(String name, String email, String username, String role) {
    }

    public record UpdateUserCommand(String name, String role, Boolean active) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserRow> list(Pageable pageable) {
        UUID brandId = BrandContext.requireBrandId();
        return userRepository.findAllByBrandId(brandId, pageable).map(this::toRow);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserRow create(CreateUserCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        String email = requireEmail(command.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("There is already an account with that email address.");
        }
        Role role = requireRole(command.role());

        User user = new User();
        user.setBrandId(brandId);
        user.setName(command.name());
        user.setEmail(email);
        user.setUsername(blankToNull(command.username()));
        user.setRole(role.name());
        user.setActive(true);
        // Nobody is told this password: the new user sets their own through the reset link.
        user.setPassword(passwordEncoder.encode(randomPassword()));
        user.setPasswordChangedAt(Instant.now());

        User saved = userRepository.save(user);
        sendInvitation(saved);

        log.info("Created user {} with role {}", saved.getId(), role);
        return toRow(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserRow update(UUID userId, UpdateUserCommand command) {
        User user = require(userId);
        UUID currentUserId = BrandContext.getCurrentUserId();

        if (command.name() != null) {
            user.setName(command.name());
        }

        if (command.role() != null) {
            Role role = requireRole(command.role());
            if (user.getId().equals(currentUserId) && role != Role.ADMIN) {
                throw ApiException.unprocessable(
                        "You cannot remove your own admin access. Ask another admin to do it.");
            }
            if (!Role.ADMIN.name().equals(user.getRole()) || role == Role.ADMIN) {
                user.setRole(role.name());
            } else {
                requireAnotherActiveAdmin(user);
                user.setRole(role.name());
            }
        }

        if (command.active() != null && command.active() != user.isActive()) {
            if (!command.active()) {
                if (user.getId().equals(currentUserId)) {
                    throw ApiException.unprocessable("You cannot deactivate your own account.");
                }
                if (Role.ADMIN.name().equals(user.getRole())) {
                    requireAnotherActiveAdmin(user);
                }
                // A deactivated user must not keep a live session.
                refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            }
            user.setActive(command.active());
        }

        return toRow(userRepository.save(user));
    }

    /** Clears a lockout after too many failed attempts, without waiting out the window. */
    @PreAuthorize("hasRole('ADMIN')")
    public UserRow unlock(UUID userId) {
        User user = require(userId);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        return toRow(userRepository.save(user));
    }

    /** Sends the user a fresh set-password link. */
    @PreAuthorize("hasRole('ADMIN')")
    public void sendPasswordReset(UUID userId) {
        sendInvitation(require(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<String> availableRoles() {
        return java.util.Arrays.stream(Role.values()).map(Enum::name).toList();
    }

    // -------------------------------------------------------------- helpers

    /**
     * A new user is never handed a password. They get the same reset link the forgotten-password
     * flow issues, valid for three days so an invitation survives a weekend.
     */
    private void sendInvitation(User user) {
        String rawToken = TokenHasher.generateToken();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenDigest(TokenHasher.digest(rawToken));
        resetToken.setTokenHash(resetToken.getTokenDigest());
        resetToken.setExpiresAt(Instant.now().plus(Duration.ofDays(3)));
        passwordResetTokenRepository.save(resetToken);

        String recipient = user.getEmail();
        emailSender.sendPasswordResetEmail(recipient, rawToken);
    }

    private void requireAnotherActiveAdmin(User user) {
        long admins = userRepository.countActiveAdmins(user.getBrandId(), user.getId());
        if (admins == 0) {
            throw ApiException.unprocessable(
                    "This is the only active admin. Promote someone else first.");
        }
    }

    private User require(UUID userId) {
        UUID brandId = BrandContext.requireBrandId();
        return userRepository.findById(userId)
                .filter(user -> brandId.equals(user.getBrandId()))
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    private static Role requireRole(String value) {
        Role role = Role.fromString(value);
        if (role == null) {
            throw ApiException.badRequest("Unknown role: " + value);
        }
        return role;
    }

    private static String requireEmail(String value) {
        if (value == null || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("A valid email address is required.");
        }
        return value.trim().toLowerCase();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UserRow toRow(User user) {
        boolean locked = user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(Instant.now());
        return new UserRow(user.getId(), user.getName(), user.getEmail(), user.getUsername(),
                user.getRole(), user.isActive(), locked, user.getLastLoginAt(), user.getCreatedAt());
    }
}
