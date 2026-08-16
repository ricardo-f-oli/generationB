package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardCommentRepository extends JpaRepository<CardComment, UUID> {

    @Query("""
        SELECT c FROM CardComment c
        WHERE c.cardId = :cardId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        ORDER BY c.createdAt ASC
        """)
    List<CardComment> findForCard(@Param("cardId") UUID cardId);

    @Query("""
        SELECT c FROM CardComment c
        WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<CardComment> findScopedById(@Param("id") UUID id);
}
