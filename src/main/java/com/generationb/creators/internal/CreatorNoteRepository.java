package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorNoteRepository extends JpaRepository<CreatorNote, UUID> {

    @Query("""
        SELECT n FROM CreatorNote n
        WHERE n.creatorId = :creatorId
          AND n.brandId = :brandId
          AND n.deletedAt IS NULL
        ORDER BY n.createdAt DESC
        """)
    List<CreatorNote> findVisibleNotes(@Param("creatorId") UUID creatorId,
                                       @Param("brandId") UUID brandId);

    @Query("SELECT n FROM CreatorNote n WHERE n.id = :id AND n.brandId = :brandId AND n.deletedAt IS NULL")
    Optional<CreatorNote> findScopedById(@Param("id") UUID id, @Param("brandId") UUID brandId);
}
