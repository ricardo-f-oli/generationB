package com.generationb.gifting.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.gifting.GiftingDtos.*;
import com.generationb.gifting.internal.GiftingExportService;
import com.generationb.gifting.internal.GiftingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gifting logistics (requirements #41–#48).
 */
@RestController
@RequestMapping("/api/gifting")
@RequiredArgsConstructor
public class GiftingController {

    private final GiftingService giftingService;
    private final GiftingExportService exportService;

    // ------------------------------------------------------------------ log

    @GetMapping("/log")
    public ApiResponse<List<DispatchResponse>> getGiftingLog() {
        return ApiResponse.of(giftingService.getGiftingLog());
    }

    // ----------------------------------------------------------------- runs

    @GetMapping("/runs")
    public ApiResponse<List<RunResponse>> listRuns() {
        return ApiResponse.of(giftingService.listRuns());
    }

    @PostMapping("/runs")
    public ApiResponse<RunResponse> createRun(@Valid @RequestBody CreateRunCommand command) {
        return ApiResponse.of(giftingService.createRun(command));
    }

    /** Requirement #44. */
    @PostMapping("/runs/{id}/approve-comp-slip")
    public ApiResponse<RunResponse> approveCompSlip(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveCompSlipCommand command) {
        return ApiResponse.of(giftingService.approveCompSlip(
                id, command == null ? null : command.mailerText()));
    }

    @PostMapping("/runs/{id}/reject-comp-slip")
    public ApiResponse<RunResponse> rejectCompSlip(@PathVariable UUID id) {
        return ApiResponse.of(giftingService.rejectCompSlip(id));
    }

    // ------------------------------------------------------------ addresses

    /** Requirement #41. */
    @PostMapping("/address-capture")
    public ApiResponse<AddressCaptureResult> requestAddresses(
            @Valid @RequestBody AddressCaptureRequest request) {
        return ApiResponse.of(giftingService.requestAddresses(request));
    }

    @GetMapping("/addresses/{creatorId}")
    public ApiResponse<AddressResponse> getAddress(@PathVariable UUID creatorId) {
        return ApiResponse.of(giftingService.getAddress(creatorId).orElse(null));
    }

    // ----------------------------------------------------------- dispatches

    /** Requirement #45. */
    @PostMapping("/dispatches")
    public ApiResponse<DispatchCreationResult> createDispatches(
            @Valid @RequestBody CreateDispatchesCommand command) {
        return ApiResponse.of(giftingService.createDispatches(command));
    }

    /** Requirement #47: RETURNED or DECLINED also flags the creator. */
    @PostMapping("/dispatches/{id}/status")
    public ApiResponse<DispatchResponse> updateDispatchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDispatchStatusCommand command) {
        return ApiResponse.of(giftingService.updateDispatchStatus(id, command));
    }

    /** Requirement #42: a real workbook, not a link to a file that was never written. */
    @GetMapping("/export/fulfilment")
    public ResponseEntity<byte[]> exportFulfilment(@RequestParam(required = false) UUID runId) {
        GiftingExportService.Export export = exportService.exportForFulfilment(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }

    // ---------------------------------------------------------- brand order

    /** Requirement #43. */
    @GetMapping("/brand-orders")
    public ApiResponse<List<BrandOrderResponse>> listBrandOrders() {
        return ApiResponse.of(giftingService.listBrandOrders());
    }

    @PostMapping("/brand-orders")
    public ApiResponse<BrandOrderResponse> createBrandOrder(
            @Valid @RequestBody CreateBrandOrderCommand command) {
        return ApiResponse.of(giftingService.createBrandOrder(command));
    }

    // ------------------------------------------------------------ reminders

    /** Requirement #46: the same pass the 10:00 schedule runs, on demand. */
    @PostMapping("/reminders/run")
    public ApiResponse<Map<String, Integer>> runReminders() {
        return ApiResponse.of(Map.of("sent", giftingService.runReminderSequence()));
    }
}
