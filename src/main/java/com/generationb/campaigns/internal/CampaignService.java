package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.BrandLookupPort;
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
    /** Only for the brand fragment in a campaign tracking hashtag, so the tag is legible. */
    private final BrandLookupPort brandLookup;

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignResponse createCampaign(CreateCampaignCommand command) {
        Campaign campaign = campaignMapper.toEntity(command);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCreatedBy(BrandContext.getCurrentUserId());
        campaign.setTrackingHashtag(generateTrackingHashtag(command.name()));
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    /**
     * The campaign's own hashtag, generated once at creation.
     *
     * <p>Readable enough that a creator can see what it is for, random enough that an organic
     * post will not collide with it. The random tail is the part that matters: without it,
     * "#summerseeding" would match strangers' posts and credit the campaign with coverage it did
     * not earn.
     *
     * <p>Derived from the name only for legibility, never re-derived. Campaign names get edited,
     * and a tag that changed afterwards would stop matching the posts already made against it.
     */
    private String generateTrackingHashtag(String campaignName) {
        String brand = brandLookup.findBrandName(BrandContext.requireBrandId())
                .map(name -> slug(name, 8))
                .orElse("gb");
        String campaign = slug(campaignName, 10);

        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = brand + campaign + randomSuffix();
            if (!campaignRepository.existsByTrackingHashtag(candidate)) {
                return candidate;
            }
        }
        // Five collisions on a 4-character tail is not going to happen, but a campaign that
        // cannot be created is worse than an ugly tag.
        return "gb" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** Lower case letters and digits only — Instagram accepts nothing else in a hashtag. */
    private static String slug(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.UK)
                .replaceAll("[^a-z0-9]", "");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    /** Four characters of randomness: 1.6 million combinations, enough to be unambiguous. */
    private static String randomSuffix() {
        String alphabet = "abcdefghijkmnpqrstuvwxyz23456789";  // no l, o, 0, 1 — they get misread
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder out = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return out.toString();
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
