package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KanbanColumnRepository extends JpaRepository<KanbanColumn, UUID> {

    @Query("""
        SELECT c FROM KanbanColumn c
        WHERE c.boardId = :boardId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        ORDER BY c.displayOrder ASC
        """)
    List<KanbanColumn> findAllByBoardIdAndBrandIdOrderByDisplayOrder(@Param("boardId") UUID boardId);

    /** Q-C7: cross-entity references must be resolved through a brand-scoped finder. */
    @Query("""
        SELECT c FROM KanbanColumn c
        WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<KanbanColumn> findScopedById(@Param("id") UUID id);

    @Query("""
        SELECT c FROM KanbanColumn c
        WHERE c.boardId = :boardId AND LOWER(c.name) = LOWER(:name)
          AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<KanbanColumn> findByBoardIdAndName(@Param("boardId") UUID boardId, @Param("name") String name);
}
