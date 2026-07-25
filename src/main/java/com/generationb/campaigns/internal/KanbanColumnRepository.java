package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface KanbanColumnRepository extends JpaRepository<KanbanColumn, UUID> {

    @Query("SELECT c FROM KanbanColumn c WHERE c.boardId = :boardId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL ORDER BY c.displayOrder ASC")
    List<KanbanColumn> findAllByBoardIdAndBrandIdOrderByDisplayOrder(@Param("boardId") UUID boardId);
}
