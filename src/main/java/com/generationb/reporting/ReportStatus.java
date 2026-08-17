package com.generationb.reporting;

/** Requirement #53: Draft to Pending Director Approval to Approved to Sent. */
public enum ReportStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SENT
}
