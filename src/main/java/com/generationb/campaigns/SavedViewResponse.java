package com.generationb.campaigns;

import java.util.Map;
import java.util.UUID;

public record SavedViewResponse(
    UUID id,
    String name,
    String scope,
    Map<String, Object> filter,
    boolean shared
) {}
