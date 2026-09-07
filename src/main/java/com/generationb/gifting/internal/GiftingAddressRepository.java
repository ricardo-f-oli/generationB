package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiftingAddressRepository extends JpaRepository<GiftingAddress, UUID> {

    // ---------------------------------------------------- retention (#37)

    /**
     * Home addresses whose purpose has passed: the parcel arrived or came back more than
     * {@code deliveredBefore} ago, or the address was captured before {@code capturedBefore} and
     * nothing was ever dispatched to it.
     */
    @Query(value = """
        SELECT a.id FROM gifting_addresses a
        WHERE (
            EXISTS (
                SELECT 1 FROM dispatches d
                WHERE d.creator_id = a.creator_id
                  AND d.status IN ('DELIVERED', 'RETURNED', 'DECLINED')
                  AND COALESCE(d.delivered_at, d.updated_at) < :deliveredBefore
            )
            OR (
                NOT EXISTS (SELECT 1 FROM dispatches d WHERE d.creator_id = a.creator_id)
                AND COALESCE(a.captured_at, a.consented_at) < :capturedBefore
            )
        )
        """, nativeQuery = true)
    List<UUID> findAddressesDueForPurge(@Param("deliveredBefore") java.time.Instant deliveredBefore,
                                        @Param("capturedBefore") java.time.Instant capturedBefore);

    @Modifying
    @Query("DELETE FROM GiftingAddress a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") List<UUID> ids);

    /** Drives the "oldest still held" figure on the GDPR screen. */
    @Query(value = "SELECT MIN(COALESCE(captured_at, consented_at)) FROM gifting_addresses",
           nativeQuery = true)
    java.time.Instant oldestAddressHeld();


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
