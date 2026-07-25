package com.generationb.outreach;

import java.util.Map;

public record ResolvedPreviewResponse(
    String resolvedSubject,
    String resolvedBody,
    Map<String, String> resolvedTokens
) {}
