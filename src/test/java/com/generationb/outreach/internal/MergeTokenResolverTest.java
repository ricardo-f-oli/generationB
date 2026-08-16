package com.generationb.outreach.internal;

import com.generationb.shared.CreatorLookupPort;
import com.generationb.foundation.BrandLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MergeTokenResolverTest {

    private static final UUID BRAND_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MergeTokenResolver tokenResolver;

    /** Minimal stubs — the point of the port (Q-E3) is that this is now trivially testable. */
    private static class StubCreatorLookup implements CreatorLookupPort {
        String lastWorkedWith;

        @Override
        public Optional<CreatorContact> findContact(UUID creatorId) {
            return Optional.empty();
        }

        @Override
        public List<CreatorContact> findContacts(List<UUID> creatorIds) {
            return List.of();
        }

        @Override
        public Optional<String> findLastWorkedWith(UUID creatorId, UUID brandId) {
            return Optional.ofNullable(lastWorkedWith);
        }

        @Override
        public boolean isSuppressed(UUID creatorId) {
            return false;
        }

        @Override
        public boolean isSuppressedByEmail(String email) {
            return false;
        }

        @Override
        public void recordSend(UUID creatorId, UUID brandId, UUID campaignId,
                               String sendType, String productName) {
        }

        @Override
        public boolean hasWorkedWithOtherBrand(UUID creatorId, UUID brandId) {
            return false;
        }
    }

    private StubCreatorLookup creatorLookup;

    @BeforeEach
    void setUp() {
        creatorLookup = new StubCreatorLookup();
        BrandLookupPort brandLookup = new BrandLookupPort() {
            @Override
            public Optional<BrandProfile> findProfile(UUID brandId) {
                return Optional.empty();
            }

            @Override
            public Optional<String> findBrandName(UUID brandId) {
                return Optional.of("BeautyBrand");
            }
        };
        tokenResolver = new MergeTokenResolver(creatorLookup, brandLookup);
    }

    @Test
    void resolvesEveryToken() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Jane");
        recipient.setCreatorHandle("@janedoe");
        recipient.setBrandId(BRAND_ID);

        OutreachCampaign campaign = new OutreachCampaign();
        campaign.setProductName("Luxe Glow Cream");

        String template = "Hi {first_name}, check out {product} for {brand} by {handle}!";
        String resolved = tokenResolver.resolveText(template, recipient, campaign, "BeautyBrand");

        assertEquals("Hi Jane, check out Luxe Glow Cream for BeautyBrand by @janedoe!", resolved);
    }

    /**
     * Q-J5: {@code {brand}} used to resolve to the brand's raw UUID, which was emailed to
     * creators verbatim. A UUID passed as the override must be ignored in favour of the name.
     */
    @Test
    void resolvesBrandNameRatherThanUuid() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Jane");
        recipient.setBrandId(BRAND_ID);

        String resolved = tokenResolver.resolveText(
                "Working with {brand}", recipient, new OutreachCampaign(), BRAND_ID.toString());

        assertEquals("Working with BeautyBrand", resolved);
        assertFalse(resolved.contains(BRAND_ID.toString()));
    }

    @Test
    void missingTokenBecomesEmptyString() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("John");
        recipient.setCreatorHandle(null);
        recipient.setBrandId(BRAND_ID);

        String resolved = tokenResolver.resolveText(
                "Hi {first_name}, your handle is {handle}.", recipient, new OutreachCampaign(), "BrandX");

        assertEquals("Hi John, your handle is .", resolved);
    }

    @Test
    void lastWorkedWithComesFromTheCreatorsModule() {
        creatorLookup.lastWorkedWith = "3 March 2026";

        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Alex");
        recipient.setCreatorId(UUID.randomUUID());
        recipient.setBrandId(BRAND_ID);

        String resolved = tokenResolver.resolveText(
                "Hello {first_name}, we last worked together on {last_worked_with}.",
                recipient, new OutreachCampaign(), "BrandY");

        assertEquals("Hello Alex, we last worked together on 3 March 2026.", resolved);
    }

    @Test
    void unresolvableLastWorkedWithDoesNotBreakTheCopy() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Alex");
        recipient.setCreatorId(UUID.randomUUID());
        recipient.setBrandId(BRAND_ID);

        String resolved = tokenResolver.resolveText(
                "Hello {first_name}, last worked with: {last_worked_with}.",
                recipient, new OutreachCampaign(), "BrandY");

        assertEquals("Hello Alex, last worked with: .", resolved);
    }
}
