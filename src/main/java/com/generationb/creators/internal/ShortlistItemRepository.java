package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShortlistItemRepository extends JpaRepository<ShortlistItem, UUID> {

    List<ShortlistItem> findByShortlistIdOrderByPosition(UUID shortlistId);

    long countByShortlistId(UUID shortlistId);

    Optional<ShortlistItem> findByShortlistIdAndCreatorId(UUID shortlistId, UUID creatorId);

    @Modifying
    @Query("DELETE FROM ShortlistItem i WHERE i.shortlistId = :shortlistId AND i.creatorId = :creatorId")
    void removeCreator(@Param("shortlistId") UUID shortlistId, @Param("creatorId") UUID creatorId);
}
