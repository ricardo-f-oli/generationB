package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiftingAddressRepository extends JpaRepository<GiftingAddress, UUID> {

    Optional<GiftingAddress> findByCreatorId(UUID creatorId);

    List<GiftingAddress> findAllByCreatorIdIn(List<UUID> creatorIds);

    /** The creator following the emailed link is not signed in, so this is not brand-scoped. */
    Optional<GiftingAddress> findByCaptureToken(String captureToken);

    /**
     * Q-G2: resolves "has a usable address?" for a whole page in one query instead of N.
     * Only rows the creator has actually completed and consented to count.
     */
    @Query("""
        SELECT a.creatorId FROM GiftingAddress a
        WHERE a.creatorId IN :creatorIds
          AND a.gdprConsentFlag = true
          AND a.street IS NOT NULL
          AND a.postalCode IS NOT NULL
        """)
    List<UUID> findCreatorIdsWithAddress(@Param("creatorIds") List<UUID> creatorIds);
}
