package com.generationb.gifting.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #43: some brands send product themselves rather than through the fulfilment house.
 * The agency emails the brand contact a request, and the brand confirms dispatch through a
 * tokenised link — so "we asked" and "they sent" are two different, recorded states.
 */
@Entity
@Table(name = "brand_orders")
@Getter
@Setter
@NoArgsConstructor
public class BrandOrder extends BaseEntity {

    public static final String REQUESTED = "REQUESTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "gifting_run_id")
    private UUID giftingRunId;

    @Column(name = "brand_contact_email")
    private String brandContactEmail;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount = 0;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status = REQUESTED;

    @Column(name = "confirm_token")
    private String confirmToken;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;
}
