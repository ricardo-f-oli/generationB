package com.generationb.foundation.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "successful", nullable = false)
    private boolean successful = false;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    public static LoginAttempt of(String identifier, String ipAddress, boolean successful) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.identifier = identifier == null ? "" : identifier.toLowerCase();
        attempt.ipAddress = ipAddress;
        attempt.successful = successful;
        return attempt;
    }
}
