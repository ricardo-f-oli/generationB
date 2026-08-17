package com.generationb.coverage.api;

import com.generationb.coverage.CoverageDtos.*;
import com.generationb.coverage.internal.CoverageExportService;
import com.generationb.coverage.internal.CoverageService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coverage tracking (requirements #11–#15).
 */
@RestController
@RequestMapping("/api/coverage")
@RequiredArgsConstructor
public class CoverageController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CoverageService coverageService;
    private final CoverageExportService exportService;

    public record ClipRequest(UUID creatorId, String creatorHandle, UUID campaignId) {
    }

    @GetMapping("/log")
    public ApiResponse<List<CoverageItemResponse>> getCoverageLog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String postType,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID creatorId,
            @RequestParam(required = false) Boolean unsolicited,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "postedAt"));

        Page<CoverageItemResponse> result = coverageService.search(
                query, platform, postType, campaignId, creatorId, unsolicited, from, to, pageable);

        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/log")
    public ApiResponse<CoverageItemResponse> create(@Valid @RequestBody CreateCoverageCommand command) {
        return ApiResponse.of(coverageService.create(command));
    }

    @DeleteMapping("/log/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        coverageService.delete(id);
        return ApiResponse.success();
    }

    /** Requirement #11: pull a creator's recent posts in. */
    @PostMapping("/clip")
    public ApiResponse<ClipResult> clipCreator(@RequestBody ClipRequest request) {
        return ApiResponse.of(coverageService.autoClipCreator(
                request.creatorId(), request.creatorHandle(), request.campaignId()));
    }

    /** Requirement #11: unsolicited coverage, found by brand name or monitored hashtags. */
    @PostMapping("/clip/mentions")
    public ApiResponse<ClipResult> clipMentions(@RequestParam(defaultValue = "25") int limit) {
        return ApiResponse.of(coverageService.clipBrandMentions(limit));
    }

    /** Requirement #14: a real workbook, not a link to a file that was never written. */
    @GetMapping("/export/{format}")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) UUID campaignId) {
        CoverageExportService.Export export = exportService.export(format, campaignId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }

    // ------------------------------------------------------------- settings

    @GetMapping("/digest-settings")
    public ApiResponse<DigestSettingsResponse> getDigestSettings() {
        return ApiResponse.of(coverageService.getDigestSettings());
    }

    @PutMapping("/digest-settings")
    public ApiResponse<DigestSettingsResponse> updateDigestSettings(
            @RequestBody UpdateDigestSettingsCommand command) {
        return ApiResponse.of(coverageService.updateDigestSettings(command));
    }

    /** Requirement #12: shows what a clipping name will look like before saving the pattern. */
    @GetMapping("/clipping-name/preview")
    public ApiResponse<Map<String, String>> previewClippingName(
            @RequestParam(required = false) String pattern) {
        return ApiResponse.of(Map.of("example", coverageService.previewClippingName(pattern)));
    }

    /** Requirement #13: send the digest now rather than waiting for the morning. */
    @PostMapping("/digest/send")
    public ApiResponse<Map<String, Boolean>> sendDigestNow() {
        return ApiResponse.of(Map.of("sent", coverageService.sendDigestNow()));
    }
}
