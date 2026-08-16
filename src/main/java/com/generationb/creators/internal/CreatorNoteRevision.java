package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Requirement #18 explicitly asks for edit history on internal notes. */
@Entity
@Table(name = "creator_note_revisions")
@Getter
@Setter
@NoArgsConstructor
public class CreatorNoteRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "note_id", nullable = false)
    private UUID noteId;

    @Column(name = "previous_text", nullable = false, columnDefinition = "text")
    private String previousText;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edited_at", nullable = false)
    private Instant editedAt = Instant.now();

    public static CreatorNoteRevision of(UUID noteId, String previousText, UUID editedBy) {
        CreatorNoteRevision revision = new CreatorNoteRevision();
        revision.noteId = noteId;
        revision.previousText = previousText;
        revision.editedBy = editedBy;
        return revision;
    }
}
