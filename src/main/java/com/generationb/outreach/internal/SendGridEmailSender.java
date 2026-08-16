package com.generationb.outreach.internal;

import com.generationb.foundation.BrandLookupPort;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SendGridEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);

    /** Per-recipient outcome, so the caller can record FAILED instead of assuming success. */
    public record BatchResult(int sent, Set<UUID> failedRecipientIds) {
    }

    private final BrandLookupPort brandLookup;

    @Value("${outreach.sendgrid.api-key:}")
    private String apiKey;

    @Value("${outreach.sendgrid.from-address:noreply@btheagency.com}")
    private String defaultFromAddress;

    @Value("${outreach.sendgrid.from-name:B. The Agency}")
    private String defaultFromName;

    @Value("${outreach.sendgrid.reply-domain:reply.btheagency.com}")
    private String replyDomain;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** When no API key is configured the sender logs instead of failing — needed for the demo. */
    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && apiKey.startsWith("SG.");
    }

    public BatchResult sendBatch(OutreachCampaign campaign, List<OutreachRecipient> recipients) {
        Set<UUID> failed = new HashSet<>();
        if (recipients == null || recipients.isEmpty()) {
            return new BatchResult(0, failed);
        }

        // Q-B4: sender identity comes from the brand profile rather than a hardcoded address.
        BrandLookupPort.BrandProfile profile =
                brandLookup.findProfile(campaign.getBrandId()).orElse(null);
        String fromAddress = profile != null && profile.replyToEmail() != null
                ? profile.replyToEmail() : defaultFromAddress;
        String fromName = profile != null && profile.fromName() != null
                ? profile.fromName() : defaultFromName;

        if (!isConfigured()) {
            log.warn("SendGrid API key not configured — {} message(s) for campaign {} were composed "
                            + "but not transmitted. Set OUTREACH_SENDGRID_API_KEY to enable sending.",
                    recipients.size(), campaign.getId());
            recipients.forEach(r -> r.setSendgridMessageId("not-configured-" + r.getId()));
            return new BatchResult(0, failed);
        }

        SendGrid client = new SendGrid(apiKey);
        int sent = 0;

        for (OutreachRecipient recipient : recipients) {
            try {
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(buildMail(recipient, fromAddress, fromName).build());

                Response response = client.api(request);
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    sent++;
                    String messageId = response.getHeaders() != null
                            ? response.getHeaders().get("X-Message-Id") : null;
                    recipient.setSendgridMessageId(
                            messageId != null && !messageId.isEmpty() ? messageId : "sg-" + recipient.getId());
                } else {
                    log.error("SendGrid rejected recipient {}: status {}", recipient.getId(),
                            response.getStatusCode());
                    failed.add(recipient.getId());
                }
            } catch (IOException ex) {
                log.error("Error sending to recipient {}", recipient.getId(), ex);
                failed.add(recipient.getId());
            }
        }

        return new BatchResult(sent, failed);
    }

    private Mail buildMail(OutreachRecipient recipient, String fromAddress, String fromName) {
        Mail mail = new Mail();
        mail.setFrom(new Email(fromAddress, fromName));
        mail.setSubject(recipient.getResolvedSubject());

        String unsubscribeUrl = frontendUrl + "/unsubscribe?token=" + recipient.getId();
        String body = recipient.getResolvedBody() == null ? "" : recipient.getResolvedBody();

        // Requirement #21 / Q-I2: every outreach email must carry a working unsubscribe route.
        String plain = body
                + "\n\n---\n"
                + "If you would rather not hear from us, unsubscribe here: " + unsubscribeUrl;
        mail.addContent(new Content("text/plain", plain));

        mail.setReplyTo(new Email(recipient.getId() + "@" + replyDomain));
        mail.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
        mail.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

        Personalization personalization = new Personalization();
        personalization.addTo(new Email(recipient.getCreatorEmail(), recipient.getCreatorFirstName()));
        personalization.addCustomArg("outreach_recipient_id", recipient.getId().toString());
        mail.addPersonalization(personalization);

        return mail;
    }

    /** Q-B4: forwards an inbound reply to the brand's own mailbox, not a hardcoded address. */
    public void forwardReplyToUser(OutreachRecipient recipient, String userEmail, String fromAddress,
                                   String subject, String bodyText, String bodyHtml) {
        if (!isConfigured() || userEmail == null || userEmail.isBlank()) {
            log.info("Inbound reply for recipient {} not forwarded (sender not configured)",
                    recipient.getId());
            return;
        }

        Mail mail = new Mail();
        mail.setFrom(new Email(defaultFromAddress, defaultFromName));
        mail.setSubject("Fwd: " + (subject != null ? subject : "Outreach reply"));
        mail.addContent(new Content("text/plain",
                "Forwarded reply from: " + fromAddress + "\n\n" + (bodyText != null ? bodyText : "")));

        Personalization personalization = new Personalization();
        personalization.addTo(new Email(userEmail));
        mail.addPersonalization(personalization);

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            new SendGrid(apiKey).api(request);
        } catch (IOException ex) {
            log.error("Error forwarding reply to {}", userEmail, ex);
        }
    }
}
