package com.generationb.campaigns;

import java.util.UUID;

public record BoardResponse(
    UUID id,
    UUID campaignId,
    UUID brandId,
    String name
) {}
