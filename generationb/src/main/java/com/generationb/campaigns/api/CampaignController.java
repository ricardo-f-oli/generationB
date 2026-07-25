package com.generationb.campaigns.api;

import com.generationb.campaigns.CampaignResponse;
import com.generationb.campaigns.CreateCampaignCommand;
import com.generationb.campaigns.internal.CampaignService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignCommand command) {
        return ApiResponse.of(campaignService.createCampaign(command));
    }

    @GetMapping
    public ApiResponse<List<CampaignResponse>> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CampaignResponse> pageResult = campaignService.listCampaigns(PageRequest.of(page, size));
        return ApiResponse.of(
                pageResult.getContent(),
                ApiResponse.Meta.of(pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements(), pageResult.getTotalPages())
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CampaignResponse> getCampaign(@PathVariable UUID id) {
        return ApiResponse.of(campaignService.getCampaign(id));
    }

    @PatchMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCampaign(@PathVariable UUID id) {
        campaignService.archiveCampaign(id);
    }
}
