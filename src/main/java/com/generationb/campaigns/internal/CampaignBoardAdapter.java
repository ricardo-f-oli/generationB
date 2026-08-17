package com.generationb.campaigns.internal;

import com.generationb.shared.CampaignBoardPort;
import com.generationb.foundation.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements {@link CampaignBoardPort} so the creators module can promote a shortlist onto a
 * board without importing anything from {@code campaigns.internal} (Q-E14, Q-E1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignBoardAdapter implements CampaignBoardPort {

    private final CampaignRepository campaignRepository;
    private final KanbanBoardRepository boardRepository;
    private final KanbanColumnRepository columnRepository;
    private final CampaignCardRepository cardRepository;
    private final KanbanService kanbanService;

    @Override
    @Transactional
    public Optional<UUID> findOrCreateBoard(UUID campaignId) {
        Campaign campaign = campaignRepository.findByIdAndBrandId(campaignId).orElse(null);
        if (campaign == null) {
            return Optional.empty();
        }
        return Optional.of(resolveBoard(campaign).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findCampaignName(UUID campaignId) {
        if (campaignId == null) {
            return Optional.empty();
        }
        return campaignRepository.findByIdAndBrandId(campaignId).map(Campaign::getName);
    }

    @Override
    @Transactional
    public PromotionResult promoteCreators(UUID campaignId, List<UUID> creatorIds, String targetColumnName) {
        Campaign campaign = campaignRepository.findByIdAndBrandId(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign"));

        KanbanBoard board = resolveBoard(campaign);

        KanbanColumn column = columnRepository.findByBoardIdAndName(board.getId(), targetColumnName)
                .orElseGet(() -> columnRepository
                        .findAllByBoardIdAndBrandIdOrderByDisplayOrder(board.getId())
                        .stream().findFirst()
                        .orElseThrow(() -> ApiException.conflict(
                                "This campaign's board has no stages configured")));

        int position = cardRepository.findMaxPosition(column.getId()) + 1;
        int created = 0;
        int skipped = 0;

        for (UUID creatorId : creatorIds) {
            if (cardRepository.existsOnBoard(board.getId(), creatorId)) {
                skipped++;
                continue;
            }
            CampaignCard card = new CampaignCard();
            card.setBoardId(board.getId());
            card.setColumnId(column.getId());
            card.setCampaignId(campaign.getId());
            card.setCreatorId(creatorId);
            card.setPosition(position++);
            cardRepository.save(card);
            created++;
        }

        log.info("Promoted {} creator(s) onto board {} (skipped {} already present)",
                created, board.getId(), skipped);
        return new PromotionResult(board.getId(), column.getId(), created, skipped);
    }

    /** A campaign gets a board on demand, built from the brand's template for its type. */
    private KanbanBoard resolveBoard(Campaign campaign) {
        return boardRepository.findByCampaignIdAndBrandId(campaign.getId())
                .orElseGet(() -> {
                    var response = kanbanService.createBoard(campaign.getId(),
                            new com.generationb.campaigns.CreateBoardCommand(campaign.getName() + " board"));
                    return boardRepository.findByIdAndBrandId(response.id())
                            .orElseThrow(() -> ApiException.conflict("Could not create a board"));
                });
    }
}
