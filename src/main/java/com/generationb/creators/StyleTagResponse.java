package com.generationb.creators;

import java.util.UUID;

public record StyleTagResponse(UUID id, String name, String category, long creatorCount) {}
