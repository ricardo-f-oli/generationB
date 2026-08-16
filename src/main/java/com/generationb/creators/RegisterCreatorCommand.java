package com.generationb.creators;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Public self-registration (requirement #20). Every field the two-step form collects is now
 * accepted — previously tags, bio, portfolio, follower band and consent were silently dropped.
 */
public record RegisterCreatorCommand(
    @NotBlank(message = "Full name is required")
    String fullName,

    @NotBlank(message = "Instagram handle is required")
    String instagram,

    String platform,
    String niche,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,

    String tiktok,
    String youtube,
    String followerBand,
    String er,
    List<String> tags,
    String bio,
    String portfolio,

    @AssertTrue(message = "Consent is required to store your details")
    boolean consentGiven
) {}
