package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorPlatformConnectionRepository
        extends JpaRepository<CreatorPlatformConnection, UUID> {

    @Query("SELECT c FROM CreatorPlatformConnection c "
         + "WHERE c.creatorId = :creatorId AND c.revokedAt IS NULL")
    List<CreatorPlatformConnection> findLiveForCreator(@Param("creatorId") UUID creatorId);

    @Query("SELECT c FROM CreatorPlatformConnection c WHERE c.creatorId = :creatorId "
         + "AND c.platform = :platform AND c.revokedAt IS NULL")
    Optional<CreatorPlatformConnection> findLive(@Param("creatorId") UUID creatorId,
                                                 @Param("platform") String platform);

    @Query("SELECT c FROM CreatorPlatformConnection c WHERE c.creatorId IN :creatorIds "
         + "AND c.revokedAt IS NULL")
    List<CreatorPlatformConnection> findLiveForCreators(@Param("creatorIds") List<UUID> creatorIds);

    /**
     * The chase list: creators with no live connection on any platform. This is the number that
     * makes the gap visible instead of it being discovered when a report comes out thin.
     */
    @Query(value = """
        SELECT c.* FROM creators c
        WHERE c.deleted_at IS NULL
          AND c.anonymised_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM creator_platform_connections pc
              WHERE pc.creator_id = c.id AND pc.revoked_at IS NULL)
        ORDER BY c.followers_count DESC
        """, nativeQuery = true)
    List<Creator> findCreatorsWithNoConnection(org.springframework.data.domain.Pageable pageable);

    @Query(value = """
        SELECT COUNT(*) FROM creators c
        WHERE c.deleted_at IS NULL
          AND c.anonymised_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM creator_platform_connections pc
              WHERE pc.creator_id = c.id AND pc.revoked_at IS NULL)
        """, nativeQuery = true)
    int countCreatorsWithNoConnection();
}
