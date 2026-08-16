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

    /** Q-G2: resolves "has an address?" for a whole page in one query instead of N. */
    @Query("SELECT a.creatorId FROM GiftingAddress a WHERE a.creatorId IN :creatorIds")
    List<UUID> findCreatorIdsWithAddress(@Param("creatorIds") List<UUID> creatorIds);
}
