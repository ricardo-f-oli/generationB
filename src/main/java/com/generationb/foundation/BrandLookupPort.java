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
        String fromName
    ) {}

    Optional<BrandProfile> findProfile(UUID brandId);

    Optional<String> findBrandName(UUID brandId);
}
