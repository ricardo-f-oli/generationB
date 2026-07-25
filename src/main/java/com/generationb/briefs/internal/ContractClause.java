package com.generationb.briefs.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.briefs.ClauseType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "contract_clauses")
@Getter
@Setter
public class ContractClause extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "clause_type", nullable = false)
    private ClauseType clauseType;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
