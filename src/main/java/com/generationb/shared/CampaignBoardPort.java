package com.generationb.shared;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Published port for other modules that need to put creators onto a campaign board.
 *
 * <p>Added so shortlist promotion (requirement #27 / Q-E14) can actually create cards — the old
 * implementation returned {@code {"success": true}} without writing anything.
 */
public interface CampaignBoardPort {

    record PromotionResult(UUID boardId, UUID columnId, int created, int skipped) {}

    /** The board for a campaign, creating one from the brand's default template if absent. */
    Optional<UUID> findOrCreateBoard(UUID campaignId);

    /**
     * The campaign's display name. Reporting needs it for creator-facing email copy, and taking
     * it from the database rather than a request parameter keeps client-supplied text out of
     * outbound mail.
     */
    Optional<String> findCampaignName(UUID campaignId);

    /**
     * Adds each creator to the named column of the campaign's board, skipping creators already
     * present. Returns what actually happened.
     */
    PromotionResult promoteCreators(UUID campaignId, List<UUID> creatorIds, String targetColumnName);
}
