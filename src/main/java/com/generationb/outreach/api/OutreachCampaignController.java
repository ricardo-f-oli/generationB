package com.generationb.outreach.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.outreach.*;
import com.generationb.outreach.internal.OutreachCampaignService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/outreach/campaigns")
public class OutreachCampaignController {

    private final OutreachCampaignService campaignService;

    public OutreachCampaignController(OutreachCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OutreachCampaignResponse>> createDraft(@RequestBody CreateOutreachDraftCommand command) {
        OutreachCampaignResponse response = campaignService.createDraft(command);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/recipients")
    public ResponseEntity<ApiResponse<OutreachCampaignResponse>> addRecipients(@PathVariable UUID id, @RequestBody List<UUID> creatorIds) {
        OutreachCampaignResponse response = campaignService.addRecipients(id, creatorIds);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}/recipients/{rid}")
    public ResponseEntity<ApiResponse<Void>> removeRecipient(@PathVariable UUID id, @PathVariable UUID rid) {
        campaignService.removeRecipient(id, rid);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/preview/{rid}")
    public ResponseEntity<ApiResponse<ResolvedPreviewResponse>> previewResolved(@PathVariable UUID id, @PathVariable UUID rid) {
        ResolvedPreviewResponse response = campaignService.previewResolved(id, rid);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<OutreachCampaignResponse>> sendNow(@PathVariable UUID id) {
        OutreachCampaignResponse response = campaignService.sendNow(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // -------------------------------- sending it yourself (#28 interim)

    /**
     * The campaign's emails, personalised and ready for a person to send from their own mailbox.
     *
     * <p>Exists because the sending domain is not authenticated yet. A domain publishing
     * {@code v=spf1 -all} does not get its mail spam-foldered, it gets it rejected, so queueing
     * outreach through the platform today would just produce bounces.
     *
     * <p>Nothing is marked as sent by this call — see {@code /mark-sent}.
     */
    @GetMapping("/{id}/manual-send")
    public ApiResponse<OutreachCampaignService.ManualSendBatch> prepareManualSend(
            @PathVariable UUID id) {
        return ApiResponse.of(campaignService.prepareManualSend(id));
    }

    /**
     * Confirms which of them the user actually sent.
     *
     * <p>Only the person who pressed send in their mail client knows this, which is why it is a
     * separate step. It writes send history, so a manually sent email still counts towards the
     * duplicate flag and the coverage reconciliation.
     */
    @PostMapping("/{id}/mark-sent")
    public ApiResponse<Map<String, Integer>> markSentManually(
            @PathVariable UUID id,
            @RequestBody(required = false) List<UUID> recipientIds) {
        return ApiResponse.of(Map.of("marked",
                campaignService.markSentManually(id, recipientIds)));
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<OutreachCampaignResponse>> scheduleSend(@PathVariable UUID id, @RequestParam Instant scheduledAt) {
        OutreachCampaignResponse response = campaignService.scheduleSend(id, scheduledAt);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<ApiResponse<List<RecipientStatusResponse>>> getRecipientsWithStatus(@PathVariable UUID id) {
        List<RecipientStatusResponse> responses = campaignService.getRecipientsWithStatus(id);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
