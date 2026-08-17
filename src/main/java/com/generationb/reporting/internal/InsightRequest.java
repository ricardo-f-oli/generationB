package com.generationb.reporting.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Requirement #52: chasing creators for the insights a report needs. */
@Entity
@Table(name = "insight_requests")
@Getter
@Setter
@NoArgsConstructor
public class InsightRequest {

    public static final String PENDING = "PENDING";
    public static final String CHASED = "CHASED";
    public static final String RECEIVED = "RECEIVED";
    public static final String WAIVED = "WAIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "status", nullable = false)
    private String status = PENDING;

    @Column(name = "chase_count", nullable = false)
    private int chaseCount = 0;

    @Column(name = "last_chased_at")
    private Instant lastChasedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
