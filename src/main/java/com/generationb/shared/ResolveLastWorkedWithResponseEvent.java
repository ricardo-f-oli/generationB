package com.generationb.shared;

import java.util.UUID;

public record ResolveLastWorkedWithResponseEvent(
    UUID requestId,
    UUID creatorId,
    String lastWorkedWith
) {}
