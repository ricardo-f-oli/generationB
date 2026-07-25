package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface KanbanBoardRepository extends JpaRepository<KanbanBoard, UUID> {

    @Query("SELECT b FROM KanbanBoard b WHERE b.campaignId = :campaignId AND b.brandId = ?#{@brandContext.brandId} AND b.deletedAt IS NULL")
    Optional<KanbanBoard> findByCampaignIdAndBrandId(@Param("campaignId") UUID campaignId);

    @Query("SELECT b FROM KanbanBoard b WHERE b.id = :id AND b.brandId = ?#{@brandContext.brandId} AND b.deletedAt IS NULL")
    Optional<KanbanBoard> findByIdAndBrandId(@Param("id") UUID id);
}
