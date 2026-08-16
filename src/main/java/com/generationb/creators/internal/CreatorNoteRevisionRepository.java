package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorNoteRevisionRepository extends JpaRepository<CreatorNoteRevision, UUID> {
    List<CreatorNoteRevision> findByNoteIdOrderByEditedAtDesc(UUID noteId);
    long countByNoteId(UUID noteId);
}
