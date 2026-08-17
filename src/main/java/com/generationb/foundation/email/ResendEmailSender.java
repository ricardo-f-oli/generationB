package com.generationb.foundation.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Production sender, via Resend. */
@Slf4j
@Service
@Profile("prod")
public class ResendEmailSender implements EmailSender {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${app.frontend-url:https://generation-bfe.vercel.app}")
    private String frontendUrl;

    @Value("${resend.from-address:Generation B <onboarding@resend.dev>}")
    private String fromAddress;

    @Value("${resend.director-address:}")
    private String directorAddress;

    @Value("${resend.team-address:}")
    private String teamAddress;

    public ResendEmailSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String url = frontendUrl + "/reset-password?token=" + resetToken;
        send(recipientEmail, "Reset your Generation B password",
                "<p>Click <a href=\"" + url + "\">here</a> to reset your password. "
                        + "The link expires in 30 minutes.</p>");
    }

    @Override
    public void sendReportApprovalRequest(UUID reportId, String reportName) {
        if (directorAddress == null || directorAddress.isBlank()) {
            log.info("No director address configured; skipping approval email for report {}", reportId);
            return;
        }
        String url = frontendUrl + "/reporting/" + reportId;
        send(directorAddress, "Report awaiting your sign-off: " + reportName,
                "<p><strong>" + escape(reportName) + "</strong> is ready for review.</p>"
                        + "<p><a href=\"" + url + "\">Open the report</a></p>");
    }

    @Override
    public void sendInsightChase(String recipientEmail, String creatorFirstName, String campaignName) {
        send(recipientEmail, "Quick favour — insights for " + campaignName,
                "<p>Hi " + escape(creatorFirstName) + ",</p>"
                        + "<p>Could you send over the insights for your "
                        + escape(campaignName) + " content when you get a moment? "
                        + "A screenshot of the post analytics is perfect.</p>"
                        + "<p>Thank you!</p>");
    }

    @Override
    public void sendAddressCaptureRequest(String recipientEmail, String creatorFirstName, String captureUrl) {
        send(recipientEmail, "Where should we send your parcel?",
                "<p>Hi " + escape(creatorFirstName) + ",</p>"
                        + "<p>We have something on the way for you. "
                        + "<a href=\"" + captureUrl + "\">Confirm your delivery address</a> "
                        + "and we will get it sent out.</p>");
    }

    @Override
    public void sendGiftReminder(String recipientEmail, String creatorFirstName,
                                 String productName, String deadlineLabel) {
        send(recipientEmail, "How are you getting on with " + productName + "?",
                "<p>Hi " + escape(creatorFirstName) + ",</p>"
                        + "<p>Just a friendly nudge — your content for "
                        + escape(productName) + " is due " + escape(deadlineLabel) + ". "
                        + "Shout if you need anything from us!</p>");
    }

    @Override
    public void sendBrandOrderRequest(String recipientEmail, String brandName,
                                      int recipientCount, String confirmUrl, String notes) {
        send(recipientEmail, "Gifting order request — " + brandName,
                "<p>Please send product to <strong>" + recipientCount + "</strong> creators "
                        + "for the upcoming campaign.</p>"
                        + (notes != null && !notes.isBlank()
                            ? "<p>Notes: " + escape(notes) + "</p>" : "")
                        + "<p><a href=\"" + confirmUrl + "\">Confirm once dispatched</a></p>");
    }

    @Override
    public void sendCoverageDigest(String recipientEmail, String htmlBody, String subject) {
        String to = (recipientEmail == null || recipientEmail.isBlank()) ? teamAddress : recipientEmail;
        if (to == null || to.isBlank()) {
            log.info("No digest recipient configured; skipping");
            return;
        }
        send(to, subject, htmlBody);
    }

    /**
     * Q-B20: the payload is built with Jackson. It used to be assembled by String.format, so a
     * quote or backslash in a name broke the JSON — and was a content-injection route.
     */
    private void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured; '{}' to {} was not sent", subject, to);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "from", fromAddress,
                    "to", List.of(to),
                    "subject", subject,
                    "html", html));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                // Q-E32: the full body is not logged — it echoes the recipient address back.
                log.error("Resend rejected '{}': status {}", subject, response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending '{}'", subject);
        } catch (Exception e) {
            log.error("Failed to send '{}' via Resend", subject, e);
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
