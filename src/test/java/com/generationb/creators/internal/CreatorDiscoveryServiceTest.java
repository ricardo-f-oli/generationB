package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.CreatorDbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreatorDiscoveryServiceTest {

    private CreatorDbLookup creatorDb;
    private CreatorDbClient client;
    private CreatorRepository creators;
    private CreatorDiscoveryService service;

    @BeforeEach
    void setUp() {
        creatorDb = mock(CreatorDbLookup.class);
        client = mock(CreatorDbClient.class);
        creators = mock(CreatorRepository.class);
        service = new CreatorDiscoveryService(creatorDb, client, creators);
        when(creatorDb.isEnabled()).thenReturn(true);
        when(client.creditsAvailable()).thenReturn(Optional.of(8123.0));
        when(creators.findActiveByAnyHandle(any())).thenReturn(List.of());
    }

    private static CreatorDiscoveryService.SearchRequest request(String platform, String country, Long min, Long max) {
        return new CreatorDiscoveryService.SearchRequest(platform, "skincare", country, min, max, null);
    }

    @Test
    @DisplayName("results already on the database carry their id, matched on any of their handles")
    void marksExistingCreators() {
        when(creatorDb.discover(any())).thenReturn(new CreatorDbLookup.DiscoveryPage(List.of(
                new CreatorDbLookup.DiscoveredCreator("TIKTOK", "Known.One", "Known", 5000L, null, null),
                new CreatorDbLookup.DiscoveredCreator("TIKTOK", "fresh", "Fresh", 4000L, null, null)),
                2, false, null));
        Creator known = new Creator();
        known.setId(UUID.randomUUID());
        known.setHandle("someone-else");
        known.setTiktokHandle("@known.one");
        when(creators.findActiveByAnyHandle(any())).thenReturn(List.of(known));

        CreatorDiscoveryService.SearchResponse response = service.search(request("tiktok", null, null, null));

        assertEquals(known.getId(), response.results().get(0).existingCreatorId());
        assertNull(response.results().get(1).existingCreatorId());
        assertEquals("https://www.tiktok.com/@Known.One", response.results().get(0).profileUrl());
        assertEquals(8123.0, response.creditsAvailable());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> handles = ArgumentCaptor.forClass(Collection.class);
        verify(creators).findActiveByAnyHandle(handles.capture());
        assertTrue(handles.getValue().containsAll(List.of("known.one", "@known.one", "fresh")));
    }

    @Test
    @DisplayName("the platform defaults to Instagram and the request is passed through")
    void passesTheQueryThrough() {
        when(creatorDb.discover(any())).thenReturn(new CreatorDbLookup.DiscoveryPage(List.of(), 0, false, null));

        service.search(new CreatorDiscoveryService.SearchRequest(null, "spf", "GB", 1000L, 5000L, 25));

        verify(creatorDb).discover(new CreatorDbLookup.DiscoveryQuery("INSTAGRAM", "spf", "GB", 1000L, 5000L, 25));
        verify(creators, never()).findActiveByAnyHandle(any());
    }

    @Test
    @DisplayName("bad input is refused before it spends a credit")
    void validatesBeforeSpending() {
        assertThrows(ApiException.class, () -> service.search(request("LINKEDIN", null, null, null)));
        assertThrows(ApiException.class, () -> service.search(request("INSTAGRAM", "England", null, null)));
        assertThrows(ApiException.class, () -> service.search(request("INSTAGRAM", null, 50_000L, 10_000L)));
        verify(creatorDb, never()).discover(any());
    }

    @Test
    @DisplayName("without CreatorDB, discovery says which variable to set")
    void disabled() {
        when(creatorDb.isEnabled()).thenReturn(false);
        assertTrue(assertThrows(ApiException.class, () -> service.search(request("INSTAGRAM", null, null, null)))
                .getMessage().contains("CREATORDB_API_KEY"));
        assertFalse(service.status().available());
    }

    @Test
    @DisplayName("profile links point at each platform's public page")
    void profileUrls() {
        assertEquals("https://www.instagram.com/glow",
                CreatorDiscoveryService.profileUrl(new CreatorDbLookup.DiscoveredCreator("INSTAGRAM", "glow", "", 1L, null, null)));
        assertEquals("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
                CreatorDiscoveryService.profileUrl(new CreatorDbLookup.DiscoveredCreator("YOUTUBE", "dan", "", 1L, null,
                        "UCabcdefghijklmnopqrstuv")));
        assertEquals("https://www.youtube.com/@dan",
                CreatorDiscoveryService.profileUrl(new CreatorDbLookup.DiscoveredCreator("YOUTUBE", "dan", "", 1L, null, null)));
    }
}
