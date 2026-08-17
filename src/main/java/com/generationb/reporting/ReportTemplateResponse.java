package com.generationb.reporting;

import java.util.List;
import java.util.UUID;

/** Requirement #50: per-brand report templates. */
public record ReportTemplateResponse(
    UUID id,
    String name,
    ReportType reportType,
    List<String> sections,
    boolean includeAffiliate,
    boolean isDefault
) {}
