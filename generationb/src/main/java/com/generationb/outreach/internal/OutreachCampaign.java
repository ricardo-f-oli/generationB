package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.outreach.OutreachCampaignStatus;
import com.generationb.outreach.OutreachType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outreach_campaigns")
@Getter
@Setter
public class OutreachCampaign extends BaseEntity {

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "product_name")
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "outreach_type", nullable = false)
    private OutreachType outreachType;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutreachCampaignStatus status = OutreachCampaignStatus.DRAFT;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "no_reply_window_days", nullable = false)
    private int noReplyWindowDays = 7;
}
