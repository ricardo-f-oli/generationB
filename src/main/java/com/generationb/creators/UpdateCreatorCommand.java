package com.generationb.creators;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** All fields optional: absent fields are left untouched (Q-E10). */
public record UpdateCreatorCommand(
    String name,
    String handle,
    @Email(message = "Must be a valid email address") String email,
    String phone,
    String primaryPlatform,
    String tiktokHandle,
    String youtubeHandle,
    @PositiveOrZero Integer followersCount,
    BigDecimal erPercentage,
    String location,
    String niche,
    String bio,
    String portfolioUrl,
    String optInStatus,
    List<UUID> tagIds
) {}
