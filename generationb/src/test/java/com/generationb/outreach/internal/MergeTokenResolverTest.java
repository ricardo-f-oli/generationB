package com.generationb.outreach.internal;

import com.generationb.outreach.RecipientStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MergeTokenResolverTest {

    private MergeTokenResolver tokenResolver;

    @BeforeEach
    void setUp() {
        tokenResolver = new MergeTokenResolver(event -> {});
    }

    @Test
    void testAllTokensResolved() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Jane");
        recipient.setCreatorHandle("@janedoe");
        recipient.setBrandId(UUID.randomUUID());

        OutreachCampaign campaign = new OutreachCampaign();
        campaign.setProductName("Luxe Glow Cream");

        String template = "Hi {first_name}, check out {product} for {brand} by {handle}!";
        String resolved = tokenResolver.resolveText(template, recipient, campaign, "BeautyBrand");

        assertEquals("Hi Jane, check out Luxe Glow Cream for BeautyBrand by @janedoe!", resolved);
    }

    @Test
    void testMissingTokenFallback() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("John");
        recipient.setCreatorHandle(null);

        OutreachCampaign campaign = new OutreachCampaign();

        String template = "Hi {first_name}, your handle is {handle}.";
        String resolved = tokenResolver.resolveText(template, recipient, campaign, "BrandX");

        assertEquals("Hi John, your handle is .", resolved);
    }

    @Test
    void testNullLastWorkedWith() {
        OutreachRecipient recipient = new OutreachRecipient();
        recipient.setCreatorFirstName("Alex");

        OutreachCampaign campaign = new OutreachCampaign();

        String template = "Hello {first_name}, last worked with: {last_worked_with}.";
        String resolved = tokenResolver.resolveText(template, recipient, campaign, "BrandY");

        assertEquals("Hello Alex, last worked with: .", resolved);
    }
}
