package com.generationb.marketing.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #48: branded waitlist landing page with email capture that auto-feeds into the
 * creator database when the platform launches.
 */
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WaitlistEntry {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CONVERTED = "CONVERTED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "name")
    private String name;

    @Column(name = "handle")
    private String handle;

    @Column(name = "primary_platform")
    private String primaryPlatform;

    @Column(name = "niche")
    private String niche;

    @Column(name = "source", nullable = false)
    private String source = "LANDING_PAGE";

    @Column(name = "status", nullable = false)
    private String status = PENDING;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "confirm_token")
    private String confirmToken;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "converted_creator_id")
    private UUID convertedCreatorId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
