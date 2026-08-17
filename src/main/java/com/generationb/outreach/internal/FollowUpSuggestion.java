package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Requirement #33: a suggested follow-up for an outreach that has gone quiet.
 *
 * <p>Q-G4: the draft lives here. It used to be written into the shared template library, which
 * meant one row per recipient per day polluting the list people pick templates from.
 */
@Entity
@Table(name = "follow_up_suggestions")
@Getter
@Setter
public class FollowUpSuggestion extends BaseEntity {

    public static final String SUGGESTED = "SUGGESTED";
    public static final String SENT = "SENT";
    public static final String DISMISSED = "DISMISSED";

    @Column(name = "outreach_recipient_id", nullable = false)
    private UUID outreachRecipientId;

    /** Optional: only set when the suggestion was built from a saved template. */
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "draft_subject")
    private String draftSubject;

    @Column(name = "draft_body")
    private String draftBody;

    @Column(name = "status", nullable = false)
    private String status = SUGGESTED;
}
