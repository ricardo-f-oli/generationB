package com.generationb.campaigns;

import java.util.UUID;

public record MoveCardRequest(
    UUID targetColumnId
) {}
