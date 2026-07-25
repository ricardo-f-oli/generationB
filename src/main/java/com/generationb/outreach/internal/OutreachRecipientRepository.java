package com.generationb.outreach.internal;

import com.generationb.outreach.RecipientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutreachRecipientRepository extends JpaRepository<OutreachRecipient, UUID> {

    List<OutreachRecipient> findByOutreachCampaignIdAndDeletedAtIsNull(UUID outreachCampaignId);

    Optional<OutreachRecipient> findBySendgridMessageId(String sendgridMessageId);

    @Query("""
        SELECT r FROM OutreachRecipient r
        JOIN OutreachCampaign c ON r.outreachCampaignId = c.id
        WHERE r.status = :status
          AND r.sentAt <= :cutoffTime
          AND r.followUpSuggestedAt IS NULL
          AND r.deletedAt IS NULL
    """)
    List<OutreachRecipient> findEligibleForFollowUp(
        @Param("status") RecipientStatus status,
        @Param("cutoffTime") Instant cutoffTime
    );
}
