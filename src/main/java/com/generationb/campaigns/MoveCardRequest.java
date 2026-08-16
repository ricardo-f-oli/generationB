package com.generationb.campaigns;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code position} is optional: null means "append to the end of the target stage". */
public record MoveCardRequest(
    @NotNull(message = "Target stage is required")
    UUID targetColumnId,
    Integer position
) {}
