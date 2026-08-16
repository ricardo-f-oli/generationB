package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorBrandLinkRepository extends JpaRepository<CreatorBrandLink, UUID> {

    Optional<CreatorBrandLink> findByCreatorIdAndBrandId(UUID creatorId, UUID brandId);

    List<CreatorBrandLink> findByCreatorId(UUID creatorId);

    @Query("SELECT l FROM CreatorBrandLink l WHERE l.creatorId IN :creatorIds")
    List<CreatorBrandLink> findAllByCreatorIds(@Param("creatorIds") List<UUID> creatorIds);

    /**
     * Cross-brand intel: has this creator been engaged by any brand other than the current one?
     * This is the duplicate flag the brief asks for when adding a creator to a new list.
     */
    @Query("""
        SELECT COUNT(l) > 0 FROM CreatorBrandLink l
        WHERE l.creatorId = :creatorId
          AND l.brandId <> :brandId
          AND l.relationshipStatus IN ('CONTACTED', 'WORKED_WITH')
        """)
    boolean existsOtherBrandEngagement(@Param("creatorId") UUID creatorId,
                                       @Param("brandId") UUID brandId);

    @Query("SELECT l.creatorId FROM CreatorBrandLink l WHERE l.brandId = :brandId")
    List<UUID> findCreatorIdsByBrandId(@Param("brandId") UUID brandId);
}
