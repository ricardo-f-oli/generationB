package com.generationb.creators.api;

import com.generationb.creators.internal.CreatorEnrichmentService;
import com.generationb.foundation.ApiResponse;
import com.generationb.foundation.insights.ModashClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The creator-data vendor, exposed to the app: discovery (#23), audience demographics (#26) and
 * the balance that pays for both.
 *
 * <p>Split out from {@link CreatorController} because these are the only creator endpoints that
 * cost money per call. Keeping them together makes the spend easy to find, easy to rate-limit
 * and easy to turn off.
 */
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorDiscoveryController {

    private final CreatorEnrichmentService enrichmentService;

    public record DiscoverRequest(String query, String platform, String niche) {
    }

    /**
     * Whether the vendor is connected and what is left to spend.
     *
     * <p>The screens read this before offering anything that costs a credit — an "Enrich" button
     * that fails on an empty balance is worse than one that is visibly unavailable.
     */
    @GetMapping("/discovery/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("live", enrichmentService.isLive());
        enrichmentService.budget().ifPresent(budget -> {
            body.put("credits", budget.credits());
            body.put("rawRequests", budget.rawRequests());
            body.put("checkedAt", budget.checkedAt());
        });
        if (!body.containsKey("credits")) {
            // Distinguishes "no vendor" and "vendor unreachable" from "nothing left".
            body.put("balanceUnavailable", true);
        }
        return ApiResponse.of(body);
    }

    /** Requirement #23: natural-language creator discovery against the vendor's index. */
    @PostMapping("/discovery/search")
    public ApiResponse<List<Map<String, Object>>> discover(@RequestBody DiscoverRequest request) {
        return ApiResponse.of(enrichmentService.discover(
                request.query(), request.platform(), request.niche()));
    }

    /** Free handle lookup — confirms an account exists before anything spends a credit on it. */
    @GetMapping("/discovery/lookup")
    public ApiResponse<List<Map<String, Object>>> lookup(
            @RequestParam String query,
            @RequestParam(defaultValue = "instagram") String platform,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.of(enrichmentService.lookup(query, platform, limit));
    }

    /**
     * Requirement #25: the creators posting about a competitor, ranked by how much they post.
     *
     * <p>Read-only on purpose. Nothing here is written to the coverage log — a competitor's
     * posts are not the client's coverage, and filing them as such would inflate every report.
     */
    @GetMapping("/discovery/competitor-mentions")
    public ApiResponse<List<Map<String, Object>>> competitorMentions(
            @RequestParam String term,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.of(enrichmentService.competitorMentions(term, limit));
    }

    /**
     * Requirement #26: pull one creator's audience demographics. Costs a credit unless the
     * figures on file are still inside the freshness window, which is why {@code force} is an
     * explicit opt-in rather than the default.
     */
    @PostMapping("/{id}/enrich")
    public ApiResponse<CreatorEnrichmentService.EnrichmentResult> enrich(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.of(enrichmentService.enrich(id, force));
    }

    /**
     * Refreshes the creators whose demographics are most out of date. Bounded by
     * {@code insights.enrichment.max-batch} however large a limit is asked for: one credit per
     * creator adds up faster than the person clicking it expects.
     */
    @PostMapping("/discovery/refresh")
    public ApiResponse<List<CreatorEnrichmentService.EnrichmentResult>> refreshStalest(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.of(enrichmentService.enrichStalest(limit, force));
    }
}
