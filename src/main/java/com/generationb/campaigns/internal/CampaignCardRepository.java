package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignCardRepository extends JpaRepository<CampaignCard, UUID> {

    @Query("SELECT c FROM CampaignCard c WHERE c.boardId = :boardId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL")
    List<CampaignCard> findAllByBoardIdAndBrandId(@Param("boardId") UUID boardId);

    @Query("SELECT c FROM CampaignCard c WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL")
    Optional<CampaignCard> findByIdAndBrandId(@Param("id") UUID id);
}
