package com.generationb.creators;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Q-E9: replaces the untyped Map<String,Object> body. */
public record CreateCreatorCommand(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @NotBlank(message = "Handle is required")
    @Size(max = 255)
    String handle,

    @Email(message = "Must be a valid email address")
    String email,

    String phone,
    String primaryPlatform,
    String tiktokHandle,
    String youtubeHandle,

    @PositiveOrZero(message = "Followers cannot be negative")
    Integer followersCount,

    BigDecimal erPercentage,
    String location,
    String niche,
    String bio,
    String portfolioUrl,
    List<UUID> tagIds
) {}
