package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #19: every product/message sent to a creator, across every brand.
 * Previously declared but never written to by any code path.
 */
@Entity
@Table(name = "creator_send_history")
@Getter
@Setter
@NoArgsConstructor
public class CreatorSendHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "send_type", nullable = false)
    private String sendType = "OUTREACH";

    @Column(name = "product_name")
    private String productName;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "duplicate_flag", nullable = false)
    private boolean duplicateFlag = false;
}
