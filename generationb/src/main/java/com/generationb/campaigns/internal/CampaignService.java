package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service managing campaigns creation, retrieval, and archiving.
 */
@Service
@Transactional
@Audited
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;

    public CampaignService(CampaignRepository campaignRepository, CampaignMapper campaignMapper) {
        this.campaignRepository = campaignRepository;
        this.campaignMapper = campaignMapper;
    }

    /**
     * Creates a new campaign context under the brand.
     *
     * @param command parameters to initialize the campaign.
     * @return the initialized CampaignResponse.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignResponse createCampaign(CreateCampaignCommand command) {
        Campaign campaign = campaignMapper.toEntity(command);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCreatedBy(BrandContext.getCurrentUserId());
        Campaign saved = campaignRepository.save(campaign);
        return campaignMapper.toResponse(saved);
    }

    /**
     * Lists campaigns for the active brand.
     *
     * @param pageable pagination details.
     * @return page of CampaignResponse.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
    public Page<CampaignResponse> listCampaigns(Pageable pageable) {
        return campaignRepository.findAllByBrandIdAndDeletedAtIsNull(pageable)
                .map(campaignMapper::toResponse);
    }

    /**
     * Gets campaign details by ID.
     *
     * @param id campaign UUID.
     * @return details of matching campaign.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
    public CampaignResponse getCampaign(UUID id) {
        Campaign campaign = campaignRepository.findByIdAndBrandId(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        return campaignMapper.toResponse(campaign);
    }

    /**
     * Archives a campaign by switching its status to ARCHIVED.
     *
     * @param id campaign UUID.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER')")
    public void archiveCampaign(UUID id) {
        Campaign campaign = campaignRepository.findByIdAndBrandId(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        campaign.setStatus(CampaignStatus.ARCHIVED);
        campaignRepository.save(campaign);
    }
}
