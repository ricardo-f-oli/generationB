package com.generationb.briefs;

import java.util.UUID;

public record ContractClauseResponse(
    UUID id,
    UUID brandId,
    ClauseType clauseType,
    String content,
    int displayOrder,
    boolean isActive
) {}
