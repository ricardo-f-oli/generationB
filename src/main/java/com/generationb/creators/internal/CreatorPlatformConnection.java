package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A creator has authorised us to read their account on one platform.
 *
 * <p>This is what replaced buying audience data from a vendor. Instagram and YouTube will report
 * a creator's own audience — age bands, gender split, countries — to an application that creator
 * has authorised, and those are the platform's own figures rather than a third party's model. It
 * costs nothing and the consent is explicit and revocable, which is a materially better position
 * than holding a profile bought from a broker.
 *
 * <p>TikTok is here too but does less: connecting yields a creator's videos and follower count,
 * and nothing else. TikTok's open API exposes no audience demographics at any tier a commercial
 * agency can reach, so nothing will ever write demographics from a TikTok connection.
 */
@Entity
@Table(name = "creator_platform_connections")
@Getter
@Setter
@NoArgsConstructor
public class CreatorPlatformConnection {

    public static final String INSTAGRAM = "INSTAGRAM";
    public static final String TIKTOK = "TIKTOK";
    public static final String YOUTUBE = "YOUTUBE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    /** The platform's own id, which survives the creator changing their handle. */
    @Column(name = "external_account_id", nullable = false, length = 128)
    private String externalAccountId;

    @Column(name = "external_username")
    private String externalUsername;

    /** Encrypted. Never logged, never returned by any endpoint. */
    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "text")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "text")
    private String refreshTokenEncrypted;

    /** Scopes get added over time; this records which connections predate a new one. */
    @Column(name = "granted_scopes", length = 500)
    private String grantedScopes;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    /** Set when the creator withdraws. The row survives as evidence; the tokens are cleared. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isLive() {
        return revokedAt == null;
    }

    /**
     * An expired token is not a revoked one — it usually just needs refreshing — but it cannot
     * be used right now, and a caller needs to tell those apart.
     */
    public boolean isUsable() {
        return isLive() && (tokenExpiresAt == null || tokenExpiresAt.isAfter(Instant.now()));
    }

    /** Requirement #26: only these two platforms report audience demographics at all. */
    public boolean canReportDemographics() {
        return INSTAGRAM.equals(platform) || YOUTUBE.equals(platform);
    }

    /** Withdrawing consent clears the credentials but keeps the record that it happened. */
    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
        this.accessTokenEncrypted = "";
        this.refreshTokenEncrypted = null;
    }
}
