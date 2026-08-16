package com.generationb.campaigns;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Requirement #4: admins define stages, including the approval gates on each one. */
public record CreateColumnCommand(
    @NotBlank(message = "Stage name is required")
    String name,
    boolean requiresDirectorApproval,
    boolean requiresClientApproval,
    boolean triggersEmail,
    UUID triggerTemplateId
) {}
