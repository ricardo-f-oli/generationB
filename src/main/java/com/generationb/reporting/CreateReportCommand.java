package com.generationb.reporting;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateReportCommand(
    String name,
    UUID campaignId,
    UUID templateId,

    @NotNull(message = "Report type is required")
    ReportType reportType,

    ReportCadence cadence,

    @NotNull(message = "Period start is required")
    LocalDate periodStart,

    @NotNull(message = "Period end is required")
    LocalDate periodEnd
) {}
