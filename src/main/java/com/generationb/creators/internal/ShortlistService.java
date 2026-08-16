package com.generationb.creators.internal;

import com.generationb.shared.CampaignBoardPort;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Requirement #27: save and share shortlists, and promote them onto a campaign board as the
 * Target List column.
 *
 * <p>Q-E14: {@code promoteToCampaign} used to return success without creating anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Audited
public class ShortlistService {

    private static final String TARGET_COLUMN = "Target List";

    private final ShortlistRepository shortlistRepository;
    private final ShortlistItemRepository itemRepository;
    private final CreatorRepository creatorRepository;
    private final CampaignBoardPort campaignBoardPort;

    public record ShortlistSummary(
            UUID id, String name, String visibility, UUID campaignId,
            long creatorCount, Instant createdAt) {
    }

    public record ShortlistDetail(
            UUID id, String name, String visibility, UUID campaignId,
            List<UUID> creatorIds, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<ShortlistSummary> findAll() {
        UUID brandId = BrandContext.requireBrandId();
        UUID userId = BrandContext.getCurrentUserId();
        return shortlistRepository.findVisible(brandId, userId).stream()
                .map(s -> new ShortlistSummary(
                        s.getId(), s.getName(), s.getVisibility(), s.getCampaignId(),
                        itemRepository.countByShortlistId(s.getId()), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShortlistDetail findById(UUID id) {
        Shortlist shortlist = requireShortlist(id);
        List<UUID> creatorIds = itemRepository.findByShortlistIdOrderByPosition(id).stream()
                .map(ShortlistItem::getCreatorId)
                .toList();
        return new ShortlistDetail(shortlist.getId(), shortlist.getName(), shortlist.getVisibility(),
                shortlist.getCampaignId(), creatorIds, shortlist.getCreatedAt());
    }

    @Transactional
    public ShortlistDetail createShortlist(String name, String visibility, UUID campaignId,
                                           List<UUID> creatorIds) {
        UUID brandId = BrandContext.requireBrandId();
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Shortlist name is required");
        }

        Shortlist shortlist = new Shortlist();
        shortlist.setBrandId(brandId);
        shortlist.setName(name.trim());
        shortlist.setVisibility(Shortlist.PRIVATE.equals(visibility) ? Shortlist.PRIVATE : Shortlist.TEAM);
        shortlist.setCampaignId(campaignId);
        shortlist.setCreatedBy(BrandContext.getCurrentUserId());
        Shortlist saved = shortlistRepository.save(shortlist);

        addCreators(saved.getId(), creatorIds);
        return findById(saved.getId());
    }

    @Transactional
    public ShortlistDetail rename(UUID id, String name, String visibility) {
        Shortlist shortlist = requireShortlist(id);
        if (name != null && !name.isBlank()) {
            shortlist.setName(name.trim());
        }
        if (visibility != null) {
            shortlist.setVisibility(Shortlist.PRIVATE.equals(visibility) ? Shortlist.PRIVATE : Shortlist.TEAM);
        }
        shortlistRepository.save(shortlist);
        return findById(id);
    }

    @Transactional
    public ShortlistDetail addCreators(UUID shortlistId, List<UUID> creatorIds) {
        Shortlist shortlist = requireShortlist(shortlistId);
        if (creatorIds == null || creatorIds.isEmpty()) {
            return findById(shortlistId);
        }

        int position = (int) itemRepository.countByShortlistId(shortlist.getId());
        for (UUID creatorId : creatorIds) {
            if (creatorRepository.findActiveById(creatorId).isEmpty()) {
                continue;
            }
            if (itemRepository.findByShortlistIdAndCreatorId(shortlist.getId(), creatorId).isPresent()) {
                continue;
            }
            itemRepository.save(ShortlistItem.of(shortlist.getId(), creatorId, position++));
        }
        return findById(shortlistId);
    }

    @Transactional
    public void removeCreator(UUID shortlistId, UUID creatorId) {
        requireShortlist(shortlistId);
        itemRepository.removeCreator(shortlistId, creatorId);
    }

    @Transactional
    public void delete(UUID id) {
        Shortlist shortlist = requireShortlist(id);
        shortlist.setDeletedAt(Instant.now());
        shortlistRepository.save(shortlist);
    }

    /**
     * Requirement #27: "Shortlists move into the campaign board as the Target List column."
     * This now really creates the cards.
     */
    @Transactional
    public Map<String, Object> promoteToCampaign(UUID shortlistId, UUID campaignId) {
        Shortlist shortlist = requireShortlist(shortlistId);
        if (campaignId == null) {
            throw ApiException.badRequest("A campaign must be selected before promoting a shortlist");
        }

        List<UUID> creatorIds = itemRepository.findByShortlistIdOrderByPosition(shortlistId).stream()
                .map(ShortlistItem::getCreatorId)
                .toList();
        if (creatorIds.isEmpty()) {
            throw ApiException.badRequest("This shortlist has no creators to promote");
        }

        CampaignBoardPort.PromotionResult result =
                campaignBoardPort.promoteCreators(campaignId, creatorIds, TARGET_COLUMN);

        shortlist.setCampaignId(campaignId);
        shortlistRepository.save(shortlist);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shortlistId", shortlistId);
        response.put("campaignId", campaignId);
        response.put("boardId", result.boardId());
        response.put("targetColumn", TARGET_COLUMN);
        response.put("promotedCount", result.created());
        response.put("alreadyOnBoard", result.skipped());
        return response;
    }

    private Shortlist requireShortlist(UUID id) {
        UUID brandId = BrandContext.requireBrandId();
        UUID userId = BrandContext.getCurrentUserId();
        return shortlistRepository.findVisibleById(id, brandId, userId)
                .orElseThrow(() -> ApiException.notFound("Shortlist"));
    }
}
