package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Q-E24: the full campaign lifecycle — create, update, archive, unarchive, delete.
 * Previously only create/list/get/archive existed, and archive did not soft-delete.
 */
@Service
@Transactional
@Audited
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignResponse createCampaign(CreateCampaignCommand command) {
        Campaign campaign = campaignMapper.toEntity(command);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCreatedBy(BrandContext.getCurrentUserId());
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    /** Q-J16: supports filtering by status. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public Page<CampaignResponse> listCampaigns(CampaignStatus status, Pageable pageable) {
        return campaignRepository.findAllFiltered(status, pageable).map(campaignMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignResponse getCampaign(UUID id) {
        return campaignMapper.toResponse(requireCampaign(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignResponse updateCampaign(UUID id, UpdateCampaignCommand command) {
        Campaign campaign = requireCampaign(id);
        if (command.name() != null && !command.name().isBlank()) {
            campaign.setName(command.name().trim());
        }
        if (command.campaignType() != null) {
            campaign.setCampaignType(command.campaignType());
        }
        if (command.status() != null) {
            campaign.setStatus(command.status());
        }
        if (command.startDate() != null) {
            campaign.setStartDate(command.startDate());
        }
        if (command.endDate() != null) {
            campaign.setEndDate(command.endDate());
        }
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER')")
    public void archiveCampaign(UUID id) {
        Campaign campaign = requireCampaign(id);
        campaign.setStatus(CampaignStatus.ARCHIVED);
        campaignRepository.save(campaign);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER')")
    public void unarchiveCampaign(UUID id) {
        Campaign campaign = requireCampaign(id);
        if (campaign.getStatus() != CampaignStatus.ARCHIVED) {
            throw ApiException.badRequest("This campaign is not archived");
        }
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaignRepository.save(campaign);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public void deleteCampaign(UUID id) {
        Campaign campaign = requireCampaign(id);
        campaign.setDeletedAt(Instant.now());
        campaignRepository.save(campaign);
    }

    private Campaign requireCampaign(UUID id) {
        return campaignRepository.findByIdAndBrandId(id)
                .orElseThrow(() -> ApiException.notFound("Campaign"));
    }
}
