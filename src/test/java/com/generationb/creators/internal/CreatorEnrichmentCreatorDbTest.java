package com.generationb.creators.internal;

import com.generationb.foundation.insights.InstagramProfiles;
import com.generationb.foundation.insights.TokenCipher;
import com.generationb.foundation.insights.YouTubeDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The profile refresh with CreatorDB configured: what it writes, what it buys, and how it falls
 * back when CreatorDB has never heard of the creator.
 */
class CreatorEnrichmentCreatorDbTest {

    private CreatorRepository creators;
    private CreatorDbLookup creatorDb;
    private AudienceReportService audienceReports;
    private CreatorEnrichmentService service;
    private Creator creator;
    private final CreatorDbLookup.Target target = new CreatorDbLookup.Target("TIKTOK", "sophia.tt");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        creators = mock(CreatorRepository.class);
        creatorDb = mock(CreatorDbLookup.class);
        audienceReports = mock(AudienceReportService.class);
        InstagramProfiles instagram = mock(InstagramProfiles.class);
        YouTubeDataClient youtube = mock(YouTubeDataClient.class);
        TokenCipher cipher = mock(TokenCipher.class);
        ObjectProvider<PlatformApiCreatorInsightsProvider> platforms = mock(ObjectProvider.class);

        service = new CreatorEnrichmentService(creators, mock(CreatorFollowerSnapshotRepository.class),
                instagram, youtube, cipher, creatorDb, audienceReports, platforms);
        ReflectionTestUtils.setField(service, "ttlHours", 20);
        ReflectionTestUtils.setField(service, "creatorDbTtlHours", 72);
        ReflectionTestUtils.setField(service, "maxBatch", 25);

        creator = new Creator();
        creator.setId(UUID.randomUUID());
        creator.setHandle("sophia.tt");
        creator.setPrimaryPlatform("TIKTOK");
        creator.setFollowersCount(10);
        when(creators.findActiveById(creator.getId())).thenReturn(Optional.of(creator));
        when(creatorDb.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("a TikTok creator — unreachable before — is refreshed from CreatorDB, demographics included")
    void tiktokIsRefreshed() {
        when(creatorDb.lookupSnapshot(target)).thenReturn(new CreatorDbLookup.SnapshotResult(
                Optional.of(new CreatorDbLookup.Snapshot(81_000L, "Fits and fails", new BigDecimal("6.40"))), false));
        when(audienceReports.fetchSummary(creator, target)).thenReturn(Map.of(
                "ukAudiencePct", 58.2, "topAgeBand", "18-24 (51%)", "genderSplit", "Female 70.0%, Male 30.0%"));

        CreatorEnrichmentService.EnrichmentResult result = service.enrich(creator.getId(), true);

        assertTrue(result.refreshed());
        assertEquals(81_000, creator.getFollowersCount());
        assertEquals(new BigDecimal("6.40"), creator.getErPercentage());
        assertEquals("Fits and fails", creator.getBio());
        assertEquals(new BigDecimal("58.20"), creator.getUkAudiencePct());
        assertEquals("18-24 (51%)", creator.getAudienceAgeBand());
        assertEquals("CREATORDB", creator.getInsightsSource());
        verify(creators).save(creator);
    }

    @Test
    @DisplayName("the daily job does not re-buy demographics a creator already has")
    void demographicsAreNotRebought() {
        creator.setAudienceGenderSplit("Female 70.0%, Male 30.0%");
        when(creatorDb.lookupSnapshot(target)).thenReturn(new CreatorDbLookup.SnapshotResult(
                Optional.of(new CreatorDbLookup.Snapshot(81_000L, null, null)), false));

        assertTrue(service.enrich(creator.getId(), false).refreshed());
        verify(audienceReports, never()).fetchSummary(any(), any());
    }

    @Test
    @DisplayName("a creator refreshed inside the CreatorDB window is skipped without a call")
    void freshCreatorsCostNothing() {
        creator.setInsightsRefreshedAt(Instant.now().minus(Duration.ofHours(30)));

        CreatorEnrichmentService.EnrichmentResult result = service.enrich(creator.getId(), false);

        assertFalse(result.refreshed());
        assertTrue(result.reason().contains("72 hours"), result.reason());
        verify(creatorDb, never()).lookupSnapshot(any());
    }

    @Test
    @DisplayName("a creator CreatorDB has not indexed is submitted, and nothing is overwritten")
    void notIndexedIsSubmitted() {
        when(creatorDb.lookupSnapshot(target)).thenReturn(new CreatorDbLookup.SnapshotResult(Optional.empty(), true));
        when(creatorDb.submit(target)).thenReturn(Optional.of("accepted"));

        CreatorEnrichmentService.EnrichmentResult result = service.enrich(creator.getId(), true);

        assertFalse(result.refreshed());
        assertTrue(result.reason().contains("submitted for indexing"), result.reason());
        verify(creatorDb).submit(target);
        assertEquals(10, creator.getFollowersCount());
        verify(creators, never()).save(any());
    }

    @Test
    @DisplayName("a CreatorDB failure is not mistaken for an unknown creator")
    void failureIsNotSubmitted() {
        when(creatorDb.lookupSnapshot(target)).thenReturn(new CreatorDbLookup.SnapshotResult(Optional.empty(), false));

        assertFalse(service.enrich(creator.getId(), true).refreshed());
        verify(creatorDb, never()).submit(any());
    }

    @Test
    @DisplayName("the source status reports CreatorDB as live on its own")
    void statusIsLive() {
        CreatorEnrichmentService.SourceStatus status = service.sourceStatus();
        assertTrue(status.live());
        assertTrue(status.creatorDb());
        assertFalse(status.instagram());
    }
}
