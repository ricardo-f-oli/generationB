package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Top-level, not nested inside BoardTemplateRepository: Spring Data does not scan repository
 * interfaces declared inside another interface, so the nested version was never registered.
 */
@Repository
public interface BoardTemplateColumnRepository extends JpaRepository<BoardTemplateColumn, UUID> {
    List<BoardTemplateColumn> findByTemplateIdOrderByDisplayOrder(UUID templateId);
}
