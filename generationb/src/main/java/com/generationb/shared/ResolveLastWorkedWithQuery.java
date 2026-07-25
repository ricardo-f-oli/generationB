package com.generationb.shared;

import java.util.UUID;

public record ResolveLastWorkedWithQuery(
    UUID requestId,
    UUID creatorId,
    UUID brandId
) {}
