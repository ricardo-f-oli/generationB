package com.generationb.reporting.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.reporting.ReportCadence;
import com.generationb.reporting.ReportStatus;
import com.generationb.reporting.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report extends BaseEntity {

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false)
    private ReportCadence cadence = ReportCadence.MONTHLY;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    /**
     * Metrics are snapshotted as JSON at generation time so an approved report does not silently
     * change when new coverage lands afterwards.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private String metrics;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}
