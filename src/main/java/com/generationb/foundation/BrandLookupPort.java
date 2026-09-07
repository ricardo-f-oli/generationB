package com.generationb.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Published port for reading brand profile facts from other modules.
 *
 * <p>Added because {@code {brand}} in outreach copy used to resolve to a raw UUID (Q-J5), and
 * because per-brand templating (requirements #1, #4, #50) needs a brand's name, tone and sender
 * identity without other modules reaching into foundation's internals.
 */
public interface BrandLookupPort {

    record BrandProfile(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String primaryColour,
        String toneOfVoice,
        String brandGuidelines,
        String instagramHandle,
        String monitoredHashtags,
        String replyToEmail,
        String fromName,

        // ------------------------------------------------- requirement #40
        /** Null when no cover has been recorded. A past date means the policy has lapsed. */
        java.time.LocalDate productLiabilityExpiresOn,
        String productLiabilityInsurer,
        Long productLiabilityCoverGbp,
        /** The wording that goes on the comp slip and the address form. */
        String giftingDisclaimer
    ) {

        /**
         * Requirement #40: whether product can be sent on this brand's behalf today.
         *
         * <p>Expiry is the case that matters. A lapsed policy looks exactly like a valid one
         * until somebody checks, which is precisely why a person checking is not the control.
         */
        public boolean hasProductLiabilityCover() {
            return productLiabilityExpiresOn != null
                    && !productLiabilityExpiresOn.isBefore(java.time.LocalDate.now());
        }

        /** Within the window where somebody should be chasing a renewal. */
        public boolean insuranceExpiringWithin(int days) {
            return productLiabilityExpiresOn != null
                    && productLiabilityExpiresOn.isAfter(java.time.LocalDate.now())
                    && productLiabilityExpiresOn.isBefore(
                            java.time.LocalDate.now().plusDays(days));
        }
    }

    Optional<BrandProfile> findProfile(UUID brandId);

    Optional<String> findBrandName(UUID brandId);
}
