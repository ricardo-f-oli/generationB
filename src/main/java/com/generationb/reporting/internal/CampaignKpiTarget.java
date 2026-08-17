package com.generationb.reporting.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Requirement #55: the client-set KPIs for a campaign. */
@Entity
@Table(name = "campaign_kpi_targets")
@Getter
@Setter
@NoArgsConstructor
public class CampaignKpiTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "min_followers")
    private Integer minFollowers;

    @Column(name = "max_followers")
    private Integer maxFollowers;

    @Column(name = "min_er")
    private BigDecimal minEr;

    @Column(name = "min_uk_audience")
    private BigDecimal minUkAudience;

    @Column(name = "target_reach")
    private Long targetReach;

    @Column(name = "preferred_platform")
    private String preferredPlatform;

    @Column(name = "preferred_niche")
    private String preferredNiche;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
