package com.generationb.briefs.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Requirement #3: a clause from the library, attached to one brief.
 *
 * <p>The table has existed since V5 and nothing in Java ever touched it, which is why the gap
 * analysis said the clause library worked but its clauses "are not yet attached to a brief" — you
 * could curate a library and then had no way to put any of it into the document a creator signs.
 *
 * <p>The clause text is deliberately <em>not</em> copied here. A clause is edited centrally when
 * the agency's legal position changes, and every brief that has not yet gone out should pick that
 * up. Briefs that have already been sent are frozen by their PDF, which is the artefact that
 * matters.
 */
@Entity
@Table(name = "brief_clauses")
@Getter
@Setter
@NoArgsConstructor
public class BriefClause {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "brief_id", nullable = false)
    private UUID briefId;

    @Column(name = "clause_id", nullable = false)
    private UUID clauseId;

    /** Order within this brief, which need not match the library's own ordering. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public BriefClause(UUID briefId, UUID clauseId, int displayOrder) {
        this.briefId = briefId;
        this.clauseId = clauseId;
        this.displayOrder = displayOrder;
    }
}
