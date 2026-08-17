package com.generationb.reporting.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.reporting.*;
import com.generationb.reporting.internal.InsightService;
import com.generationb.reporting.internal.KpiService;
import com.generationb.reporting.internal.ReportExportService;
import com.generationb.reporting.internal.ReportService;
import com.generationb.shared.CampaignBoardPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reporting and insights (requirements #49–#55).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReportService reportService;
    private final ReportExportService exportService;
    private final InsightService insightService;
    private final KpiService kpiService;
    private final CampaignBoardPort campaignBoard;

    public record RejectRequest(String reason) {
    }

    public record TemplateRequest(String name, ReportType reportType,
                                  List<String> sections, boolean includeAffiliate) {
    }

    public record MatchRequest(List<UUID> creatorIds) {
    }

    // ------------------------------------------------------------- reports

    @GetMapping
    public ApiResponse<List<ReportResponse>> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ReportResponse> result = reportService.list(status, campaignId, pageable);
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReportResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(reportService.get(id));
    }

    @PostMapping
    public ApiResponse<ReportResponse> create(@Valid @RequestBody CreateReportCommand command) {
        return ApiResponse.of(reportService.create(command));
    }

    /**
     * Live figures for a period without saving a report — powers the "what would this say?"
     * panel before anyone commits to a draft.
     */
    @GetMapping("/preview")
    public ApiResponse<ReportMetrics> preview(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reportService.preview(campaignId, from, to));
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<ReportResponse> regenerate(@PathVariable UUID id) {
        return ApiResponse.of(reportService.regenerate(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ReportResponse> submit(@PathVariable UUID id) {
        return ApiResponse.of(reportService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ReportResponse> approve(@PathVariable UUID id) {
        return ApiResponse.of(reportService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ReportResponse> reject(@PathVariable UUID id,
                                              @RequestBody(required = false) RejectRequest request) {
        return ApiResponse.of(reportService.reject(id, request == null ? null : request.reason()));
    }

    /** Requirement #53: 422 unless a director has signed the report off. */
    @PostMapping("/{id}/send")
    public ApiResponse<ReportResponse> send(@PathVariable UUID id) {
        return ApiResponse.of(reportService.sendToClient(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        reportService.delete(id);
        return ApiResponse.success();
    }

    /** Requirement #54: pdf | excel | powerpoint. */
    @GetMapping("/{id}/export/{format}")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @PathVariable String format) {
        ReportExportService.Export export = exportService.export(id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }

    // ----------------------------------------------------------- templates

    @GetMapping("/templates")
    public ApiResponse<List<ReportTemplateResponse>> listTemplates() {
        return ApiResponse.of(reportService.listTemplates());
    }

    @PostMapping("/templates")
    public ApiResponse<ReportTemplateResponse> createTemplate(@RequestBody TemplateRequest request) {
        return ApiResponse.of(reportService.createTemplate(
                request.name(), request.reportType(), request.sections(), request.includeAffiliate()));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable UUID id) {
        reportService.deleteTemplate(id);
        return ApiResponse.success();
    }

    // ------------------------------------------------------------ insights

    @GetMapping("/insights/{campaignId}")
    public ApiResponse<List<InsightService.InsightRow>> insights(@PathVariable UUID campaignId) {
        return ApiResponse.of(insightService.list(campaignId));
    }

    /** Rebuilds the outstanding list from who was sent to versus who has posted. */
    @PostMapping("/insights/{campaignId}/refresh")
    public ApiResponse<List<InsightService.InsightRow>> refreshInsights(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(insightService.refresh(campaignId, from, to));
    }

    @PostMapping("/insights/{campaignId}/chase-all")
    public ApiResponse<Map<String, Integer>> chaseAll(@PathVariable UUID campaignId) {
        int chased = insightService.chaseAll(campaignId, campaignName(campaignId));
        return ApiResponse.of(Map.of("chased", chased));
    }

    @PostMapping("/insights/request/{requestId}/chase")
    public ApiResponse<InsightService.InsightRow> chase(@PathVariable UUID requestId,
                                                        @RequestParam(required = false) UUID campaignId) {
        return ApiResponse.of(insightService.chase(requestId, campaignName(campaignId)));
    }

    @PostMapping("/insights/request/{requestId}/received")
    public ApiResponse<Void> markReceived(@PathVariable UUID requestId) {
        insightService.markReceived(requestId);
        return ApiResponse.success();
    }

    // ----------------------------------------------------------------- KPI

    @GetMapping("/kpi/{campaignId}")
    public ApiResponse<KpiTargetResponse> kpi(@PathVariable UUID campaignId) {
        return ApiResponse.of(kpiService.get(campaignId));
    }

    @PutMapping("/kpi/{campaignId}")
    public ApiResponse<KpiTargetResponse> upsertKpi(@PathVariable UUID campaignId,
                                                    @RequestBody KpiTargetResponse request) {
        return ApiResponse.of(kpiService.upsert(campaignId, request));
    }

    /** Requirement #55: the match indicator shown against each shortlisted creator. */
    @PostMapping("/kpi/{campaignId}/match")
    public ApiResponse<List<KpiMatchResponse>> match(@PathVariable UUID campaignId,
                                                     @RequestBody MatchRequest request) {
        List<UUID> ids = request == null || request.creatorIds() == null
                ? List.of() : request.creatorIds();
        return ApiResponse.of(kpiService.match(campaignId, ids));
    }

    private String campaignName(UUID campaignId) {
        return campaignId == null ? null : campaignBoard.findCampaignName(campaignId).orElse(null);
    }
}
