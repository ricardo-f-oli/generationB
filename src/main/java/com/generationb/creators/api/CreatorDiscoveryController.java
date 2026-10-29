package com.generationb.creators.api;

import com.generationb.creators.internal.AudienceReportService;
import com.generationb.creators.internal.CreatorDiscoveryService;
import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Requirements #23 and #26 through CreatorDB: finding creators who are not on the database yet,
 * and the full audience report for one who is.
 *
 * <p>Both spend credits, so the reads that are free are kept separate from the calls that buy:
 * {@code GET .../audience-report} returns what was bought before; {@code POST} buys a new one.
 */
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorDiscoveryController {

    private final CreatorDiscoveryService discoveryService;
    private final AudienceReportService audienceReportService;

    /** Whether discovery is configured, and the credit balance last seen. Free. */
    @GetMapping("/discovery/status")
    public ApiResponse<CreatorDiscoveryService.Status> status() {
        return ApiResponse.of(discoveryService.status());
    }

    /** One page of creators from CreatorDB. 1 credit. */
    @PostMapping("/discovery/search")
    public ApiResponse<CreatorDiscoveryService.SearchResponse> search(
            @RequestBody CreatorDiscoveryService.SearchRequest request) {
        return ApiResponse.of(discoveryService.search(request));
    }

    /** Stored audience reports and the platforms one could be bought for. Free. */
    @GetMapping("/{id}/audience-report")
    public ApiResponse<AudienceReportService.Overview> audienceReports(@PathVariable UUID id) {
        return ApiResponse.of(audienceReportService.overview(id));
    }

    /** Buys a fresh audience report for one platform (the primary when omitted). 10 credits. */
    @PostMapping("/{id}/audience-report")
    public ApiResponse<AudienceReportService.Report> refreshAudienceReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String platform) {
        return ApiResponse.of(audienceReportService.refresh(id, platform));
    }
}
