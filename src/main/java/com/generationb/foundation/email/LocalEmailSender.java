package com.generationb.foundation.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Development sender: logs what would have been sent.
 *
 * <p>Q-E30: Mailpit was dropped from docker-compose because nothing was ever wired to it. The
 * console is the honest local channel.
 */
@Slf4j
@Service
@Profile("!prod")
public class LocalEmailSender implements EmailSender {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        log(recipientEmail, "Password reset",
                frontendUrl + "/reset-password?token=" + resetToken);
    }

    @Override
    public void sendReportApprovalRequest(UUID reportId, String reportName) {
        log("directors", "Report awaiting sign-off: " + reportName,
                frontendUrl + "/reporting/" + reportId);
    }

    @Override
    public void sendInsightChase(String recipientEmail, String creatorFirstName, String campaignName) {
        log(recipientEmail, "Insights chase for " + campaignName, "Hi " + creatorFirstName);
    }

    @Override
    public void sendAddressCaptureRequest(String recipientEmail, String creatorFirstName, String captureUrl) {
        log(recipientEmail, "Delivery address request", captureUrl);
    }

    @Override
    public void sendGiftReminder(String recipientEmail, String creatorFirstName,
                                 String productName, String deadlineLabel) {
        log(recipientEmail, "Gift reminder: " + productName, deadlineLabel);
    }

    @Override
    public void sendBrandOrderRequest(String recipientEmail, String brandName,
                                      int recipientCount, String confirmUrl, String notes) {
        log(recipientEmail, "Order request for " + brandName + " (" + recipientCount + " recipients)",
                confirmUrl);
    }

    @Override
    public void sendCoverageDigest(String recipientEmail, String htmlBody, String subject) {
        log(recipientEmail, subject, htmlBody.length() + " characters of HTML");
    }

    private void log(String to, String subject, String detail) {
        log.info("[LOCAL EMAIL] to={} | {} | {}", to, subject, detail);
    }
}
