package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorSendHistoryRepository extends JpaRepository<CreatorSendHistory, UUID> {

    List<CreatorSendHistory> findByCreatorIdOrderBySentAtDesc(UUID creatorId);

    /** Q-J3: ordered, so "last worked with" really is the most recent send. */
    @Query("""
        SELECT h FROM CreatorSendHistory h
        WHERE h.creatorId = :creatorId AND h.brandId = :brandId
        ORDER BY h.sentAt DESC
        LIMIT 1
        """)
    Optional<CreatorSendHistory> findMostRecentForBrand(@Param("creatorId") UUID creatorId,
                                                        @Param("brandId") UUID brandId);

    @Query("""
        SELECT COUNT(h) > 0 FROM CreatorSendHistory h
        WHERE h.creatorId = :creatorId AND h.brandId <> :brandId
        """)
    boolean existsForOtherBrand(@Param("creatorId") UUID creatorId, @Param("brandId") UUID brandId);

    /** Requirement #15: which creators this brand sent to inside the window. */
    @Query(value = """
        SELECT DISTINCT creator_id
        FROM creator_send_history
        WHERE brand_id = :brandId
          AND (CAST(:campaignId AS uuid) IS NULL OR campaign_id = :campaignId)
          AND (CAST(:from AS date) IS NULL OR sent_at >= CAST(:from AS date))
          AND (CAST(:to   AS date) IS NULL OR sent_at < CAST(:to AS date) + INTERVAL '1 day')
        """, nativeQuery = true)
    List<UUID> distinctCreatorsSent(@Param("brandId") java.util.UUID brandId,
                                    @Param("campaignId") java.util.UUID campaignId,
                                    @Param("from") java.time.LocalDate from,
                                    @Param("to") java.time.LocalDate to);
}
