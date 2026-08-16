package com.generationb.campaigns.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "board_template_columns")
@Getter
@Setter
@NoArgsConstructor
public class BoardTemplateColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

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
