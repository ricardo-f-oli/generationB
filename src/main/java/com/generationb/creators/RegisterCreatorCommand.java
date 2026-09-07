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
    // Five questions rather than one box. A single tick covering storage, marketing, address
    // sharing and content reuse is not specific consent to any of them under UK GDPR, and it is
    // worthless as evidence if a creator later says they never agreed to a brand seeing their
    // rates. Only the first is required, because without it there is nothing to store.

    /** 1. "Keep my details on file so you can consider me for campaigns." */
    @AssertTrue(message = "We need your permission to keep your details on file")
    boolean consentGiven,

    /** 2. "Email me about paid campaigns and collaborations." */
    boolean consentMarketingEmail,

    /** 3. "Send me products to try, sharing my address with the fulfilment partner." */
    boolean consentGiftingAddress,

    /** 4. "Share my profile and rates with the client brands you work with." */
    boolean consentBrandSharing,

    /** 5. "Use my posted content in campaign reports and your own marketing." */
    boolean consentContentReuse
) {

    /**
     * The questions, as the public form shows them. Kept beside the fields so the wording, the
     * stored column and the consent record cannot drift apart — the text a creator agreed to is
     * the thing that has to be reproducible later.
     */
    public record Question(String key, String label, boolean required) {}

    public static List<Question> questions() {
        return List.of(
            new Question("consentGiven",
                "Keep my details on file so Generation B can consider me for campaigns.", true),
            new Question("consentMarketingEmail",
                "Email me about paid campaigns and collaborations that suit my content.", false),
            new Question("consentGiftingAddress",
                "Send me products to try. I understand my address is shared with the "
                    + "fulfilment partner who posts the parcel.", false),
            new Question("consentBrandSharing",
                "Share my profile, audience figures and rates with the client brands "
                    + "Generation B works with.", false),
            new Question("consentContentReuse",
                "Use posts I have published in campaign reports and in Generation B's own "
                    + "marketing.", false)
        );
    }
}
