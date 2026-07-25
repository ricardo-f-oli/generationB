package com.generationb.campaigns;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BulkMoveCardsRequest(
    @NotEmpty(message = "Card IDs are required")
    List<UUID> cardIds,
    @NotNull(message = "Target column ID is required")
    UUID targetColumnId
) {}
