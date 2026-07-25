package com.generationb.campaigns.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "kanban_columns")
@Getter
@Setter
public class KanbanColumn extends BaseEntity {

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "requires_director_approval", nullable = false)
    private boolean requiresDirectorApproval = false;

    @Column(name = "requires_client_approval", nullable = false)
    private boolean requiresClientApproval = false;

    @Column(name = "triggers_email", nullable = false)
    private boolean triggersEmail = false;

    @Column(name = "trigger_template_id")
    private UUID triggerTemplateId;
}
