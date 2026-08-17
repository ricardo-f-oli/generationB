package com.generationb.reporting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsightRequestRepository extends JpaRepository<InsightRequest, UUID> {

    @Query("""
        SELECT i FROM InsightRequest i
        WHERE i.brandId = :brandId
          AND (:campaignId IS NULL OR i.campaignId = :campaignId)
        """)
    List<InsightRequest> findForCampaign(@Param("brandId") UUID brandId,
                                         @Param("campaignId") UUID campaignId);

    Optional<InsightRequest> findByCampaignIdAndCreatorId(UUID campaignId, UUID creatorId);

    @Query("""
        SELECT i FROM InsightRequest i
        WHERE i.brandId = :brandId
          AND (:campaignId IS NULL OR i.campaignId = :campaignId)
          AND i.status IN ('PENDING', 'CHASED')
        """)
    List<InsightRequest> findOutstanding(@Param("brandId") UUID brandId,
                                         @Param("campaignId") UUID campaignId);
}
