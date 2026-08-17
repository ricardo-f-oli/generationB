package com.generationb.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportResponse(
    UUID id,
    UUID brandId,
    UUID campaignId,
    UUID templateId,
    String name,
    ReportType reportType,
    ReportCadence cadence,
    LocalDate periodStart,
    LocalDate periodEnd,
    ReportStatus status,
    ReportMetrics metrics,
    UUID submittedBy,
    Instant submittedAt,
    UUID approvedBy,
    Instant approvedAt,
    Instant sentAt,
    String rejectionReason,
    Instant createdAt
) {}
