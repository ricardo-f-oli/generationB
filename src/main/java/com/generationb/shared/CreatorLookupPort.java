package com.generationb.shared;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Published port for other modules that need creator facts.
 *
 * <p>Q-E3 / Q-E1: outreach used to ask for a creator's contact details by publishing an
 * ApplicationEvent and then immediately reading a shared map, which only worked because Spring
 * events happen to be synchronous — adding {@code @Async} anywhere would have silently sent every
 * email to {@code creator-<uuid>@example.com}. Gifting went further and imported
 * {@code creators.internal.CreatorRepository} directly, breaking the module boundary.
 *
 * <p>A plain interface is simpler than both, and is what Spring Modulith expects a module to
 * expose.
 */
public interface CreatorLookupPort {

    record CreatorContact(
        UUID creatorId,
        String email,
        String firstName,
        String fullName,
        String handle
    ) {}

    Optional<CreatorContact> findContact(UUID creatorId);

    List<CreatorContact> findContacts(List<UUID> creatorIds);

    /** Human-readable date of the last send to this creator for this brand, or empty. */
    Optional<String> findLastWorkedWith(UUID creatorId, UUID brandId);

    /** Requirement #21: never send to a creator on the global suppression list. */
    boolean isSuppressed(UUID creatorId);

    boolean isSuppressedByEmail(String email);

    /** Requirement #19: records that a brand sent something to a creator. */
    void recordSend(UUID creatorId, UUID brandId, UUID campaignId, String sendType, String productName);

    /** Requirement #19: cross-brand duplicate flag when adding a creator to a new list. */
    boolean hasWorkedWithOtherBrand(UUID creatorId, UUID brandId);

    // -------------------------------------------------------------- gifting

    /**
     * Requirement #47: a refusal or a returned parcel excludes the creator from future gifting.
     * The flag is global rather than per-brand — a creator who has asked not to be sent product
     * should not be sent it by a sister brand either.
     */
    void flagGiftingExclusion(UUID creatorId, String reason);

    boolean isGiftingExcluded(UUID creatorId);

    // ------------------------------------------------------------ reporting

    /** Enough of a creator profile for a report row and a KPI comparison (#49, #55). */
    record CreatorProfile(
        UUID creatorId,
        String handle,
        String name,
        Integer followersCount,
        java.math.BigDecimal erPercentage,
        java.math.BigDecimal ukAudiencePct,
        String qualityBand,
        String primaryPlatform,
        String niche
    ) {}

    /** Requirement #49: follower growth needs two points in time, not one mutable number. */
    record FollowerGrowth(UUID creatorId, int startFollowers, int endFollowers, int delta) {}

    List<CreatorProfile> profiles(List<UUID> creatorIds);

    /** Requirement #15: which creators this brand actually sent to in the window. */
    List<UUID> creatorsSentTo(UUID brandId, UUID campaignId,
                              java.time.LocalDate from, java.time.LocalDate to);

    List<FollowerGrowth> followerGrowth(List<UUID> creatorIds,
                                        java.time.LocalDate from, java.time.LocalDate to);

    /** Records today's follower count so growth can be measured later. */
    void captureFollowerSnapshots();
}
