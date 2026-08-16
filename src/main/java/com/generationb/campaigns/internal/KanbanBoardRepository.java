package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface KanbanBoardRepository extends JpaRepository<KanbanBoard, UUID> {

    /**
     * A campaign has one live board (enforced by a partial unique index in V26). ORDER BY + LIMIT
     * keeps this deterministic rather than throwing if legacy data ever contains duplicates.
     */
    @Query("""
        SELECT b FROM KanbanBoard b
        WHERE b.campaignId = :campaignId
          AND b.brandId = ?#{@brandContext.brandId}
          AND b.deletedAt IS NULL
        ORDER BY b.createdAt ASC
        LIMIT 1
        """)
    Optional<KanbanBoard> findByCampaignIdAndBrandId(@Param("campaignId") UUID campaignId);

    @Query("SELECT b FROM KanbanBoard b WHERE b.id = :id AND b.brandId = ?#{@brandContext.brandId} AND b.deletedAt IS NULL")
    Optional<KanbanBoard> findByIdAndBrandId(@Param("id") UUID id);
}
