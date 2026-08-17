package com.generationb.foundation.email;

import java.util.UUID;

/** Transactional email leaving the platform. Outreach to creators goes via the outreach module. */
public interface EmailSender {

    void sendPasswordResetEmail(String recipientEmail, String resetToken);

    /** Requirement #53: the sign-off gate is "triggered by automated email to director". */
    void sendReportApprovalRequest(UUID reportId, String reportName);

    /** Requirement #52: chase a creator for missing insights. */
    void sendInsightChase(String recipientEmail, String creatorFirstName, String campaignName);

    /** Requirement #41: ask a creator to confirm their delivery address. */
    void sendAddressCaptureRequest(String recipientEmail, String creatorFirstName, String captureUrl);

    /** Requirement #46: the post-delivery reminder sequence. */
    void sendGiftReminder(String recipientEmail, String creatorFirstName,
                          String productName, String deadlineLabel);

    /** Requirement #43: ask a brand to fulfil an order themselves. */
    void sendBrandOrderRequest(String recipientEmail, String brandName,
                               int recipientCount, String confirmUrl, String notes);

    /** Requirement #13: the daily coverage digest. */
    void sendCoverageDigest(String recipientEmail, String htmlBody, String subject);
}
