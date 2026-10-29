package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.CreatorDbClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Requirement #23: finding creators who are not on the database yet, through CreatorDB's index.
 *
 * <p>Each search costs one credit and returns a page of handles with follower counts. Results
 * already on the database are marked with their id, so the screen links to them instead of
 * offering to add a duplicate. Adding one is the ordinary create-creator call; its figures are
 * filled by the next profile refresh.
 */
@Service
@RequiredArgsConstructor
public class CreatorDiscoveryService {

    public record SearchRequest(String platform, String keywords, String country,
                                Long minFollowers, Long maxFollowers, Integer offset) {
    }

    public record Result(String platform, String handle, String name, Long followers,
                         String avatarUrl, String profileUrl, UUID existingCreatorId) {
    }

    public record SearchResponse(List<Result> results, long totalResults, boolean hasNextPage,
                                 Integer nextOffset, Double creditsAvailable) {
    }

    public record Status(boolean available, Double creditsAvailable) {
    }

    private final CreatorDbLookup creatorDb;
    private final CreatorDbClient client;
    private final CreatorRepository creatorRepository;

    public Status status() {
        return new Status(creatorDb.isEnabled(), client.creditsAvailable().orElse(null));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public SearchResponse search(SearchRequest request) {
        if (!creatorDb.isEnabled()) {
            throw ApiException.unprocessable(
                    "Creator discovery needs CreatorDB configured on the server (CREATORDB_API_KEY).");
        }
        String platform = request.platform() == null || request.platform().isBlank()
                ? CreatorDbLookup.INSTAGRAM : request.platform().trim().toUpperCase(Locale.ROOT);
        if (!CreatorDbLookup.PLATFORMS.contains(platform)) {
            throw ApiException.unprocessable("Platform must be Instagram, TikTok or YouTube.");
        }
        if (request.country() != null && !request.country().isBlank()
                && CreatorDbLookup.alpha3(request.country()) == null) {
            throw ApiException.unprocessable("Country must be an ISO code such as GB or GBR.");
        }
        if (request.minFollowers() != null && request.maxFollowers() != null
                && request.maxFollowers() > 0 && request.minFollowers() > request.maxFollowers()) {
            throw ApiException.unprocessable("Minimum followers is above the maximum.");
        }

        CreatorDbLookup.DiscoveryPage page = creatorDb.discover(new CreatorDbLookup.DiscoveryQuery(
                platform, request.keywords(), request.country(), request.minFollowers(),
                request.maxFollowers(), request.offset() == null ? 0 : request.offset()));

        Map<String, UUID> existing = existingByHandle(page.creators());
        List<Result> results = page.creators().stream()
                .map(c -> new Result(c.platform(), c.handle(), c.name(), c.followers(), c.avatarUrl(),
                        profileUrl(c), existing.get(c.handle().toLowerCase(Locale.ROOT))))
                .toList();
        return new SearchResponse(results, page.totalResults(), page.hasNextPage(), page.nextOffset(),
                client.creditsAvailable().orElse(null));
    }

    private Map<String, UUID> existingByHandle(List<CreatorDbLookup.DiscoveredCreator> creators) {
        if (creators.isEmpty()) {
            return Map.of();
        }
        Set<String> handles = new HashSet<>();
        for (CreatorDbLookup.DiscoveredCreator c : creators) {
            String lower = c.handle().toLowerCase(Locale.ROOT);
            handles.add(lower);
            handles.add("@" + lower);
        }
        Map<String, UUID> out = new HashMap<>();
        for (Creator creator : creatorRepository.findActiveByAnyHandle(handles)) {
            for (String stored : new String[] {creator.getHandle(), creator.getTiktokHandle(), creator.getYoutubeHandle()}) {
                if (stored != null) {
                    out.putIfAbsent(stored.trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT), creator.getId());
                }
            }
        }
        return out;
    }

    static String profileUrl(CreatorDbLookup.DiscoveredCreator c) {
        return switch (c.platform()) {
            case CreatorDbLookup.TIKTOK -> "https://www.tiktok.com/@" + c.handle();
            case CreatorDbLookup.YOUTUBE -> c.channelId() != null
                    ? "https://www.youtube.com/channel/" + c.channelId()
                    : "https://www.youtube.com/@" + c.handle();
            default -> "https://www.instagram.com/" + c.handle();
        };
    }
}
