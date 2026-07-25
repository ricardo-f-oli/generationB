package com.generationb.campaigns;

import jakarta.validation.constraints.NotBlank;

public record CreateBoardCommand(
    @NotBlank(message = "Board name is required")
    String name
) {}
