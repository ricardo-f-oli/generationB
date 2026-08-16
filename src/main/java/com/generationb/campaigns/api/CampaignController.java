package com.generationb.campaigns.api;

import com.generationb.campaigns.*;
import com.generationb.campaigns.internal.CampaignService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignCommand command) {
        return ApiResponse.of(campaignService.createCampaign(command));
    }

    @GetMapping
    public ApiResponse<List<CampaignResponse>> listCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CampaignResponse> result = campaignService.listCampaigns(
                status, PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CampaignResponse> getCampaign(@PathVariable UUID id) {
        return ApiResponse.of(campaignService.getCampaign(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CampaignResponse> updateCampaign(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateCampaignCommand command) {
        return ApiResponse.of(campaignService.updateCampaign(id, command));
    }

    @PatchMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCampaign(@PathVariable UUID id) {
        campaignService.archiveCampaign(id);
    }

    @PatchMapping("/{id}/unarchive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unarchiveCampaign(@PathVariable UUID id) {
        campaignService.unarchiveCampaign(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCampaign(@PathVariable UUID id) {
        campaignService.deleteCampaign(id);
    }
}
