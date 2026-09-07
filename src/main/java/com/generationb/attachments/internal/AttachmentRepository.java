package com.generationb.attachments.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @Query("""
        SELECT a FROM Attachment a
        WHERE a.brandId = ?#{@brandContext.brandId}
          AND a.deletedAt IS NULL
          AND a.ownerType = :ownerType
          AND a.ownerId = :ownerId
        ORDER BY a.createdAt DESC
        """)
    List<Attachment> findForOwner(@Param("ownerType") String ownerType,
                                  @Param("ownerId") UUID ownerId);

    @Query("""
        SELECT a FROM Attachment a
        WHERE a.id = :id AND a.brandId = ?#{@brandContext.brandId} AND a.deletedAt IS NULL
        """)
    Optional<Attachment> findScopedById(@Param("id") UUID id);

    /** Drives the "3 files" badge on a board card without loading every row. */
    @Query("""
        SELECT a.ownerId, COUNT(a) FROM Attachment a
        WHERE a.brandId = ?#{@brandContext.brandId}
          AND a.deletedAt IS NULL
          AND a.ownerType = :ownerType
          AND a.ownerId IN :ownerIds
        GROUP BY a.ownerId
        """)
    List<Object[]> countByOwner(@Param("ownerType") String ownerType,
                                @Param("ownerIds") List<UUID> ownerIds);
}
