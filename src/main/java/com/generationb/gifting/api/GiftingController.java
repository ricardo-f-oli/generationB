package com.generationb.gifting.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.gifting.internal.Dispatch;
import com.generationb.gifting.internal.GiftingRun;
import com.generationb.gifting.internal.GiftingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gifting")
@RequiredArgsConstructor
public class GiftingController {

    private final GiftingService giftingService;

    @GetMapping("/log")
    public ApiResponse<List<Map<String, Object>>> getGiftingLog() {
        return ApiResponse.of(giftingService.getGiftingLog());
    }

    @PostMapping("/address-capture")
    public ApiResponse<Map<String, Object>> sendAddressCapture(@RequestBody Map<String, List<String>> payload) {
        List<String> recipientIds = payload.getOrDefault("recipientIds", List.of());
        Map<String, Object> res = giftingService.sendAddressCaptureEmails(recipientIds);
        return ApiResponse.of(res);
    }

    @PostMapping("/export/ec-group")
    public ApiResponse<Map<String, String>> exportEcGroup() {
        return ApiResponse.of(giftingService.exportEcGroupExcel());
    }

    @PostMapping("/direct-brand-order")
    public ApiResponse<Map<String, Object>> sendDirectBrandOrder(@RequestBody Map<String, Object> payload) {
        String brandName = (String) payload.getOrDefault("brandName", "Katie Loxton");
        @SuppressWarnings("unchecked")
        List<String> recipientIds = (List<String>) payload.getOrDefault("recipientIds", List.of());
        String notes = (String) payload.get("notes");

        Map<String, Object> res = giftingService.sendDirectBrandOrder(brandName, recipientIds, notes);
        return ApiResponse.of(res);
    }

    @PostMapping("/comp-slips/{id}/approve")
    public ApiResponse<GiftingRun> approveCompSlip(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        String mailerText = payload.get("mailerText");
        GiftingRun run = giftingService.approveCompSlip(id, mailerText);
        return ApiResponse.of(run);
    }

    @PostMapping("/dispatches/{id}/status")
    public ApiResponse<Dispatch> updateDispatchStatus(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        String trackingNumber = payload.get("trackingNumber");
        String returnReason = payload.get("returnReason");

        Dispatch dispatch = giftingService.updateDispatchStatus(id, status, trackingNumber, returnReason);
        return ApiResponse.of(dispatch);
    }
}
