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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deliverables", columnDefinition = "jsonb")
    private List<String> deliverables;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", length = 3)
    private String feeCurrency;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_draft_urls", columnDefinition = "jsonb")
    private List<String> contentDraftUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
