package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorStyleTagLinkRepository
        extends JpaRepository<CreatorStyleTagLink, CreatorStyleTagLink.Key> {

    List<CreatorStyleTagLink> findByCreatorId(UUID creatorId);

    @Query("SELECT l.creatorId FROM CreatorStyleTagLink l WHERE l.tagId = :tagId")
    List<UUID> findCreatorIdsByTagId(@Param("tagId") UUID tagId);

    @Modifying
    @Query("DELETE FROM CreatorStyleTagLink l WHERE l.creatorId = :creatorId")
    void deleteByCreatorId(@Param("creatorId") UUID creatorId);

    @Modifying
    @Query("DELETE FROM CreatorStyleTagLink l WHERE l.creatorId = :creatorId AND l.tagId = :tagId")
    void deleteLink(@Param("creatorId") UUID creatorId, @Param("tagId") UUID tagId);
}
