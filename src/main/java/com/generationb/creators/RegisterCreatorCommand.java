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

    // ---------------------------------------------------- requirement #20
    //
    // Two questions. The client asked for fewer tickboxes (27 Oct 2026), so the first now covers
    // everything joining actually means — being kept on file, shown to brands and emailed about
    // campaigns — and says so in one sentence. Content reuse stays a separate, optional choice
    // because it is not something registering implies. Gifting addresses are no longer asked
    // for here: the address form asks for that consent itself, when there is a parcel to send.

    /** 1. Required: keep my details, share my profile with brands, email me about campaigns. */
    @AssertTrue(message = "We need your permission to keep your details on file")
    boolean consentGiven,

    /** 2. Optional: use my published posts in generation b.'s own marketing. */
    boolean consentContentReuse
) {

    /**
     * Bumped whenever the wording below changes, and stored on every consent record, so the
     * exact sentence a creator agreed to can always be reproduced. v1 was the five-question form.
     */
    public static final String POLICY_VERSION = "v2";

    /**
     * The questions, as the public form shows them. Kept beside the fields so the wording, the
     * stored column and the consent record cannot drift apart — the text a creator agreed to is
     * the thing that has to be reproducible later.
     */
    public record Question(String key, String label, boolean required) {}

    public static List<Question> questions() {
        return List.of(
            new Question("consentGiven",
                "Keep my details on file, share my profile with the brands b. the agency works "
                    + "with, and email me about campaigns that suit my content.", true),
            new Question("consentContentReuse",
                "Use posts I have published in campaign reports and in generation b.'s own "
                    + "marketing.", false)
        );
    }
}
