package com.generationb.creators.api;

import com.generationb.creators.internal.CreatorEnrichmentService;
import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Requirement #26: refreshing creators already in the database from the platforms' free public
 * APIs — Instagram Business Discovery and the YouTube Data API. No vendor, no credits, and no
 * permission needed from the creator.
 */
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorProfileRefreshController {

    private final CreatorEnrichmentService enrichmentService;

    /** Which sources are configured, so the screen can explain an unavailable button. */
    @GetMapping("/profile-refresh/status")
    public ApiResponse<CreatorEnrichmentService.SourceStatus> status() {
        return ApiResponse.of(enrichmentService.sourceStatus());
    }

    /**
     * Refreshes one creator's follower count and bio. Skipped while the figures are still inside
     * the freshness window unless {@code force} is set.
     */
    @PostMapping("/{id}/enrich")
    public ApiResponse<CreatorEnrichmentService.EnrichmentResult> enrich(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.of(enrichmentService.enrich(id, force));
    }

    /** Refreshes the creators whose figures are most out of date, bounded by the batch size. */
    @PostMapping("/profile-refresh")
    public ApiResponse<List<CreatorEnrichmentService.EnrichmentResult>> refreshStalest(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.of(enrichmentService.enrichStalest(limit, force));
    }
}
