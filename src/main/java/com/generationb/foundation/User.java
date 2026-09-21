package com.generationb.foundation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // --- Brute-force protection (Q-B10) ---

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /**
     * The account is on a temporary password — seeded, or handed over by someone else. Every
     * request except change-password, me and logout is refused until the user sets their own.
     *
     * <p>This is enforced server-side in {@link com.generationb.foundation.internal.PasswordChangeGate}
     * rather than by the frontend alone: a flag the UI merely respects is bypassed by anyone
     * who calls the API directly with the token the login just handed them.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public Role roleEnum() {
        return Role.fromString(role);
    }
}
