package com.generationb.shared;

import java.util.UUID;

public record ResolveCreatorContactQuery(
    UUID requestId,
    UUID creatorId,
    UUID brandId
) {}
