package com.generationb.shared;

import java.util.UUID;

public record ResolveCreatorContactResponseEvent(
    UUID requestId,
    UUID creatorId,
    UUID brandId,
    String creatorEmail,
    String creatorFirstName,
    String creatorHandle
) {}
