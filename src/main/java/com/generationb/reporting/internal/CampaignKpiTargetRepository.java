package com.generationb.reporting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignKpiTargetRepository extends JpaRepository<CampaignKpiTarget, UUID> {
    Optional<CampaignKpiTarget> findByCampaignId(UUID campaignId);
}
