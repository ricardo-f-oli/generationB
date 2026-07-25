package com.generationb.outreach.internal;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class SendGridEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);

    @Value("${outreach.sendgrid.api-key:SG.mock-key}")
    private String apiKey;

    @Value("${outreach.sendgrid.from-address:noreply@btheagency.com}")
    private String fromAddress;

    @Value("${outreach.sendgrid.from-name:B. The Agency}")
    private String fromName;

    public void sendBatch(OutreachCampaign campaign, List<OutreachRecipient> recipients, String userSenderEmail) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        SendGrid sg = new SendGrid(apiKey);

        for (OutreachRecipient recipient : recipients) {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromAddress, fromName));
            mail.setSubject(recipient.getResolvedSubject());

            Content content = new Content("text/plain", recipient.getResolvedBody());
            mail.addContent(content);

            // Reply-To header tagged address
            String taggedReplyTo = recipient.getId() + "@reply.btheagency.com";
            mail.setReplyTo(new Email(taggedReplyTo));

            Personalization personalization = new Personalization();
            personalization.addTo(new Email(recipient.getCreatorEmail(), recipient.getCreatorFirstName()));
            personalization.addCustomArg("outreach_recipient_id", recipient.getId().toString());
            mail.addPersonalization(personalization);

            try {
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());

                Response response = sg.api(request);
                log.info("Sent email to recipient {}: status code {}", recipient.getId(), response.getStatusCode());

                // Store SendGrid Message ID header or standard ID if returned
                String messageIdHeader = response.getHeaders().get("X-Message-Id");
                if (messageIdHeader != null && !messageIdHeader.isEmpty()) {
                    recipient.setSendgridMessageId(messageIdHeader);
                } else {
                    recipient.setSendgridMessageId("sg-" + recipient.getId());
                }
            } catch (IOException ex) {
                log.error("Error sending email via SendGrid to recipient {}", recipient.getId(), ex);
                recipient.setSendgridMessageId("failed-" + recipient.getId());
            }
        }
    }

    public void forwardReplyToUser(OutreachRecipient recipient, String userEmail, String fromAddress, String subject, String bodyText, String bodyHtml) {
        SendGrid sg = new SendGrid(apiKey);
        Mail mail = new Mail();
        mail.setFrom(new Email(this.fromAddress, "B. The Agency Inbound Forwarder"));
        mail.setSubject("Fwd: " + (subject != null ? subject : "Outreach Reply"));

        String textContent = "Forwarded reply from: " + fromAddress + "\n\n" + (bodyText != null ? bodyText : "");
        mail.addContent(new Content("text/plain", textContent));
        if (bodyHtml != null && !bodyHtml.isEmpty()) {
            mail.addContent(new Content("text/html", bodyHtml));
        }

        Personalization personalization = new Personalization();
        personalization.addTo(new Email(userEmail));
        mail.addPersonalization(personalization);

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            log.info("Forwarded inbound reply to user {}: status code {}", userEmail, response.getStatusCode());
        } catch (IOException ex) {
            log.error("Error forwarding reply to user {}", userEmail, ex);
        }
    }
}
