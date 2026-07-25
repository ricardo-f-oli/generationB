package com.generationb.outreach.internal;

import com.generationb.outreach.OutreachCampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutreachCampaignRepository extends JpaRepository<OutreachCampaign, UUID> {

    List<OutreachCampaign> findByBrandIdAndDeletedAtIsNull(UUID brandId);

    @Query("SELECT c FROM OutreachCampaign c WHERE c.status = :status AND c.scheduledAt <= :now AND c.deletedAt IS NULL")
    List<OutreachCampaign> findScheduledToRun(@Param("status") OutreachCampaignStatus status, @Param("now") Instant now);
}
