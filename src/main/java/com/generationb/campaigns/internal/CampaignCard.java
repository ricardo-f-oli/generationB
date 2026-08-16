package com.generationb.campaigns.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.campaigns.ApprovalStatus;
import com.generationb.campaigns.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaign_cards")
@Getter
@Setter
public class CampaignCard extends BaseEntity {

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "column_id", nullable = false)
    private UUID columnId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    /** Q-E20: deterministic ordering, which drag-and-drop needs. */
    @Column(name = "position", nullable = false)
    private int position = 0;

    /** Requirement #5: the card carries its brief. */
    @Column(name = "brief_id")
    private UUID briefId;

    /** Requirement #9: "my cards" needs an owner. */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deliverables", columnDefinition = "jsonb")
    private List<String> deliverables;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", length = 3)
    private String feeCurrency = "GBP";

    @Column(name = "deadline")
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_draft_urls", columnDefinition = "jsonb")
    private List<String> contentDraftUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
