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
 * A batch of product going out — the thing a comp slip is approved against (requirement #44).
 */
@Entity
@Table(name = "gifting_runs")
@Getter
@Setter
@NoArgsConstructor
public class GiftingRun extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "name")
    private String name;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "product_name")
    private String productName;

    /** The comp slip copy that goes in the box. */
    @Column(name = "mailer_text")
    private String mailerText;

    @Column(name = "comp_slip_status", nullable = false)
    private String compSlipStatus = PENDING;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public boolean isApproved() {
        return APPROVED.equals(compSlipStatus);
    }
}
