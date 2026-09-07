package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A creator is GLOBAL to the agency — one row per human (Q-C6).
 *
 * <p>It deliberately no longer extends {@code BaseEntity}: a {@code brand_id} on the creator is
 * what made cross-brand duplicate detection and cross-brand send history impossible. Which client
 * brands a creator has worked with is recorded in {@link CreatorBrandLink} instead.
 */
@Entity
@Table(name = "creators")
@Getter
@Setter
@NoArgsConstructor
public class Creator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "handle", nullable = false)
    private String handle;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "primary_platform", nullable = false)
    private String primaryPlatform = "INSTAGRAM";

    @Column(name = "tiktok_handle")
    private String tiktokHandle;

    @Column(name = "youtube_handle")
    private String youtubeHandle;

    @Column(name = "followers_count", nullable = false)
    private Integer followersCount = 0;

    @Column(name = "follower_band")
    private String followerBand;

    @Column(name = "er_percentage", nullable = false)
    private BigDecimal erPercentage = BigDecimal.ZERO;

    @Column(name = "location")
    private String location;

    @Column(name = "niche")
    private String niche;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    // --- Audience demographics (requirement #26). Populated by the insights provider. ---

    @Column(name = "uk_audience_pct")
    private BigDecimal ukAudiencePct;

    @Column(name = "audience_age_band")
    private String audienceAgeBand;

    @Column(name = "audience_gender_split")
    private String audienceGenderSplit;

    @Column(name = "quality_band")
    private String qualityBand;

    /**
     * Where the four fields above came from: {@code MODASH}, or null when a person typed them.
     * Without this a reader cannot tell a measured figure from a guess someone made in 2024.
     */
    @Column(name = "insights_source")
    private String insightsSource;

    /** The vendor's own id for this account, so a renamed handle still resolves. */
    @Column(name = "insights_external_id")
    private String insightsExternalId;

    /** When the vendor last answered. A report costs a credit, so staleness is checked first. */
    @Column(name = "insights_refreshed_at")
    private Instant insightsRefreshedAt;

    // -------------------------------------------- consent (requirement #20)
    //
    // The answers to the five opt-in questions. consent_records remains the audit trail of every
    // grant and withdrawal; these are the current state, so a screen or a send check can read
    // them without aggregating the evidence table.

    /** Requirement #21: false blocks outreach regardless of the suppression list. */
    @Column(name = "consent_marketing_email", nullable = false)
    private boolean consentMarketingEmail = false;

    @Column(name = "consent_gifting_address", nullable = false)
    private boolean consentGiftingAddress = false;

    @Column(name = "consent_brand_sharing", nullable = false)
    private boolean consentBrandSharing = false;

    @Column(name = "consent_content_reuse", nullable = false)
    private boolean consentContentReuse = false;

    @Column(name = "opt_in_status", nullable = false)
    private String optInStatus = "APPROVED";

    @Column(name = "opt_in_step", nullable = false)
    private Integer optInStep = 5;

    /** Q-I3: right to erasure is implemented as anonymisation, recorded here. */
    @Column(name = "anonymised_at")
    private Instant anonymisedAt;

    /**
     * Requirement #47: set when a creator refuses a gift or a parcel comes back, so they are
     * excluded from future gifting selections until someone clears the flag.
     */
    @Column(name = "gifting_excluded", nullable = false)
    private boolean giftingExcluded = false;

    @Column(name = "gifting_exclusion_reason")
    private String giftingExclusionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isAnonymised() {
        return anonymisedAt != null;
    }

    /** Follower band derived from the count when the creator did not self-report one. */
    public String resolvedFollowerBand() {
        if (followerBand != null && !followerBand.isBlank()) {
            return followerBand;
        }
        int count = followersCount == null ? 0 : followersCount;
        if (count < 10_000) return "Under 10K";
        if (count < 50_000) return "10K-50K";
        if (count < 100_000) return "50K-100K";
        if (count < 250_000) return "100K-250K";
        return "250K+";
    }
}
