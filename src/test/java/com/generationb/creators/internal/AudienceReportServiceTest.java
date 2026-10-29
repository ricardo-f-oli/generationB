package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.CreatorDbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Buying, storing and reading audience reports. The rule that matters most: a report is only
 * bought on request, and what was bought is kept.
 */
class AudienceReportServiceTest {

    private static final String AUDIENCE =
            "{\"audienceLocations\":[{\"country\":\"GBR\",\"share\":0.62},{\"country\":\"USA\",\"share\":0.2}],"
                    + "\"audienceGender\":{\"maleRatio\":0.25,\"femaleRatio\":0.75},\"audienceAvgAge\":27,"
                    + "\"audienceAgeBreakdown\":[{\"ageRange\":\"18-24\",\"share\":0.4},{\"ageRange\":\"25-34\",\"share\":0.45}]}";

    private final ObjectMapper mapper = new ObjectMapper();
    private CreatorRepository creators;
    private CreatorAudienceReportRepository reports;
    private CreatorDbLookup creatorDb;
    private AudienceReportService service;
    private Creator creator;

    @BeforeEach
    void setUp() {
        creators = mock(CreatorRepository.class);
        reports = mock(CreatorAudienceReportRepository.class);
        creatorDb = mock(CreatorDbLookup.class);
        service = new AudienceReportService(creators, reports, creatorDb, mapper);

        creator = new Creator();
        creator.setId(UUID.randomUUID());
        creator.setHandle("sophia");
        creator.setPrimaryPlatform("INSTAGRAM");
        creator.setTiktokHandle("sophia.tt");
        when(creators.findActiveById(creator.getId())).thenReturn(Optional.of(creator));
        when(creatorDb.isEnabled()).thenReturn(true);
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reports.findByCreatorIdAndPlatform(any(), any())).thenReturn(Optional.empty());
    }

    private CreatorDbClient.Result answer(String json) throws Exception {
        return new CreatorDbClient.Result(Optional.of(mapper.readTree(json)), "");
    }

    @Test
    @DisplayName("buying the primary platform's report stores it and updates the profile summary")
    void refreshStoresAndSummarises() throws Exception {
        when(creatorDb.audienceRaw(new CreatorDbLookup.Target("INSTAGRAM", "sophia"))).thenReturn(answer(AUDIENCE));

        AudienceReportService.Report report = service.refresh(creator.getId(), null);

        assertEquals("INSTAGRAM", report.platform());
        assertEquals("sophia", report.handle());
        assertEquals("GB", report.countries().getFirst().key());
        assertEquals(new BigDecimal("62.00"), report.countries().getFirst().pct());
        assertEquals(27, report.averageAge());

        ArgumentCaptor<CreatorAudienceReport> saved = ArgumentCaptor.forClass(CreatorAudienceReport.class);
        verify(reports).save(saved.capture());
        assertEquals(creator.getId(), saved.getValue().getCreatorId());
        assertTrue(saved.getValue().getReport().contains("audienceLocations"), "the raw answer is kept");

        assertEquals(new BigDecimal("62.00"), creator.getUkAudiencePct());
        assertEquals("25-34 (45%)", creator.getAudienceAgeBand());
        assertEquals("Female 75.0%, Male 25.0%", creator.getAudienceGenderSplit());
        verify(creators).save(creator);
    }

    @Test
    @DisplayName("a secondary platform's report is stored but leaves the profile summary alone")
    void secondaryPlatformDoesNotOverwriteSummary() throws Exception {
        when(creatorDb.audienceRaw(new CreatorDbLookup.Target("TIKTOK", "sophia.tt"))).thenReturn(answer(AUDIENCE));

        service.refresh(creator.getId(), "tiktok");

        verify(reports).save(any());
        assertNull(creator.getUkAudiencePct());
        verify(creators, never()).save(any());
    }

    @Test
    @DisplayName("refreshing replaces the stored report rather than adding a second")
    void refreshUpdatesInPlace() throws Exception {
        CreatorAudienceReport existing = new CreatorAudienceReport();
        existing.setId(UUID.randomUUID());
        existing.setReport("{}");
        when(reports.findByCreatorIdAndPlatform(creator.getId(), "INSTAGRAM")).thenReturn(Optional.of(existing));
        when(creatorDb.audienceRaw(any())).thenReturn(answer(AUDIENCE));

        service.refresh(creator.getId(), "INSTAGRAM");

        assertTrue(existing.getReport().contains("GBR"));
        verify(reports).save(existing);
    }

    @Test
    @DisplayName("a creator CreatorDB has not indexed is submitted, and the message says so")
    void notIndexedIsSubmitted() {
        CreatorDbLookup.Target target = new CreatorDbLookup.Target("INSTAGRAM", "sophia");
        when(creatorDb.audienceRaw(target)).thenReturn(new CreatorDbClient.Result(Optional.empty(), "NOT_FOUND"));
        when(creatorDb.submit(target)).thenReturn(Optional.of("accepted"));

        ApiException e = assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null));

        assertTrue(e.getMessage().contains("submitted for indexing"), e.getMessage());
        verify(creatorDb).submit(target);
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("an audience CreatorDB could not model is explained, not stored as zeros")
    void emptyAudienceIsExplained() throws Exception {
        when(creatorDb.audienceRaw(any())).thenReturn(answer(
                "{\"audienceLocations\":null,\"audienceGender\":null,\"audienceAvgAge\":null,\"audienceAgeBreakdown\":null}"));

        ApiException e = assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null));

        assertTrue(e.getMessage().contains("no audience model"), e.getMessage());
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("spent credits are explained in words the team can act on")
    void creditErrorsAreExplained() {
        when(creatorDb.audienceRaw(any())).thenReturn(new CreatorDbClient.Result(Optional.empty(), "INSUFFICIENT_CREDITS"));
        assertTrue(assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null))
                .getMessage().contains("credits are spent"));

        when(creatorDb.audienceRaw(any())).thenReturn(new CreatorDbClient.Result(Optional.empty(), CreatorDbClient.RESERVE));
        assertTrue(assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null))
                .getMessage().contains("reserve"));
    }

    @Test
    @DisplayName("a platform the creator has no handle on is refused before any call")
    void unknownPlatformIsRefused() {
        assertThrows(ApiException.class, () -> service.refresh(creator.getId(), "YOUTUBE"));
        verify(creatorDb, never()).audienceRaw(any());
    }

    @Test
    @DisplayName("without CreatorDB, buying is refused with the variable to set")
    void disabledIsRefused() {
        when(creatorDb.isEnabled()).thenReturn(false);
        ApiException e = assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null));
        assertTrue(e.getMessage().contains("CREATORDB_API_KEY"));
    }

    @Test
    @DisplayName("an anonymised creator never has a report bought")
    void anonymisedIsRefused() {
        creator.setAnonymisedAt(Instant.now());
        assertThrows(ApiException.class, () -> service.refresh(creator.getId(), null));
        verify(creatorDb, never()).audienceRaw(any());
    }

    @Test
    @DisplayName("the overview reads stored reports for free and lists the platforms on file")
    void overviewIsFree() {
        CreatorAudienceReport stored = new CreatorAudienceReport();
        stored.setPlatform("TIKTOK");
        stored.setReport(AUDIENCE);
        when(reports.findByCreatorIdOrderByPlatformAsc(creator.getId())).thenReturn(List.of(stored));

        AudienceReportService.Overview overview = service.overview(creator.getId());

        assertTrue(overview.available());
        assertEquals(List.of(
                new AudienceReportService.PlatformHandle("INSTAGRAM", "sophia"),
                new AudienceReportService.PlatformHandle("TIKTOK", "sophia.tt")), overview.platforms());
        assertEquals(1, overview.reports().size());
        assertEquals("sophia.tt", overview.reports().getFirst().handle());
        verify(creatorDb, never()).audienceRaw(any());
    }

    @Test
    @DisplayName("the profile refresh's purchase is stored too, so the 10 credits buy both")
    void fetchSummaryStores() throws Exception {
        CreatorDbLookup.Target target = new CreatorDbLookup.Target("INSTAGRAM", "sophia");
        when(creatorDb.audienceRaw(target)).thenReturn(answer(AUDIENCE));

        Map<String, Object> summary = service.fetchSummary(creator, target);

        assertEquals(62.0, summary.get("ukAudiencePct"));
        verify(reports).save(any());
    }
}
