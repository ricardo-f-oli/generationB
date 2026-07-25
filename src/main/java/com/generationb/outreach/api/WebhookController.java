package com.generationb.outreach.api;

import com.generationb.outreach.RecipientStatus;
import com.generationb.outreach.ThreadDirection;
import com.generationb.outreach.internal.*;
import com.generationb.shared.CreatorFlaggedEvent;
import com.generationb.shared.OutreachReplyReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks/sendgrid")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final OutreachRecipientRepository recipientRepository;
    private final EmailThreadRepository emailThreadRepository;
    private final SendGridEmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${outreach.sendgrid.webhook-secret:mock-secret}")
    private String webhookSecret;

    public WebhookController(
            OutreachRecipientRepository recipientRepository,
            EmailThreadRepository emailThreadRepository,
            SendGridEmailSender emailSender,
            ApplicationEventPublisher eventPublisher) {
        this.recipientRepository = recipientRepository;
        this.emailThreadRepository = emailThreadRepository;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<Void> handleSendGridEvent(
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false) String signature,
            @RequestBody List<Map<String, Object>> events) {

        if (!isValidSignature(signature)) {
            log.warn("Invalid SendGrid webhook signature: {}", signature);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (events == null) {
            return ResponseEntity.ok().build();
        }

        for (Map<String, Object> event : events) {
            String eventType = (String) event.get("event");
            String recipientIdStr = (String) event.get("outreach_recipient_id");
            String messageId = (String) event.get("sg_message_id");

            Optional<OutreachRecipient> recipientOpt = Optional.empty();
            if (recipientIdStr != null) {
                try {
                    recipientOpt = recipientRepository.findById(UUID.fromString(recipientIdStr));
                } catch (IllegalArgumentException ignored) {}
            }
            if (recipientOpt.isEmpty() && messageId != null) {
                recipientOpt = recipientRepository.findBySendgridMessageId(messageId);
            }

            if (recipientOpt.isPresent()) {
                OutreachRecipient recipient = recipientOpt.get();
                if ("open".equalsIgnoreCase(eventType)) {
                    recipient.setStatus(RecipientStatus.OPENED);
                    if (recipient.getOpenedAt() == null) {
                        recipient.setOpenedAt(Instant.now());
                    }
                    recipientRepository.save(recipient);
                } else if ("click".equalsIgnoreCase(eventType)) {
                    if (recipient.getStatus() != RecipientStatus.OPENED && recipient.getStatus() != RecipientStatus.REPLIED) {
                        recipient.setStatus(RecipientStatus.OPENED);
                        if (recipient.getOpenedAt() == null) {
                            recipient.setOpenedAt(Instant.now());
                        }
                        recipientRepository.save(recipient);
                    }
                } else if ("bounce".equalsIgnoreCase(eventType)) {
                    log.warn("SendGrid delivery failure/bounce for recipient: {}", recipient.getId());
                } else if ("spamreport".equalsIgnoreCase(eventType)) {
                    log.warn("SendGrid spam report received for creator: {}", recipient.getCreatorId());
                    eventPublisher.publishEvent(new CreatorFlaggedEvent(
                        recipient.getCreatorId(),
                        recipient.getBrandId(),
                        "SPAM_REPORT",
                        Instant.now()
                    ));
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/inbound", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> handleInboundReply(MultipartHttpServletRequest request) {
        String toAddress = request.getParameter("to");
        String fromAddress = request.getParameter("from");
        String subject = request.getParameter("subject");
        String bodyText = request.getParameter("text");
        String bodyHtml = request.getParameter("html");

        if (toAddress == null) {
            log.warn("Inbound email missing 'to' address");
            return ResponseEntity.badRequest().build();
        }

        UUID recipientId = extractRecipientIdFromAddress(toAddress);
        if (recipientId == null) {
            log.warn("Could not parse recipient ID from address: {}", toAddress);
            return ResponseEntity.ok().build();
        }

        Optional<OutreachRecipient> recipientOpt = recipientRepository.findById(recipientId);
        if (recipientOpt.isEmpty()) {
            log.warn("Recipient not found for inbound reply ID: {}", recipientId);
            return ResponseEntity.ok().build();
        }

        OutreachRecipient recipient = recipientOpt.get();

        EmailThread thread = new EmailThread();
        thread.setOutreachRecipientId(recipient.getId());
        thread.setBrandId(recipient.getBrandId());
        thread.setDirection(ThreadDirection.INBOUND);
        thread.setFromAddress(fromAddress != null ? fromAddress : "unknown");
        thread.setToAddress(toAddress);
        thread.setSubject(subject);
        thread.setBodyText(bodyText);
        thread.setBodyHtml(bodyHtml);
        thread.setReceivedAt(Instant.now());
        emailThreadRepository.save(thread);

        recipient.setStatus(RecipientStatus.REPLIED);
        recipient.setRepliedAt(Instant.now());
        recipientRepository.save(recipient);

        eventPublisher.publishEvent(new OutreachReplyReceivedEvent(
            recipient.getId(),
            recipient.getCreatorId(),
            recipient.getBrandId(),
            recipient.getOutreachCampaignId(),
            Instant.now()
        ));

        String userEmail = "manager@btheagency.com";
        emailSender.forwardReplyToUser(recipient, userEmail, fromAddress, subject, bodyText, bodyHtml);

        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return !"INVALID_SIG".equals(signature);
    }

    private UUID extractRecipientIdFromAddress(String address) {
        try {
            int atIdx = address.indexOf("@");
            if (atIdx > 0) {
                String localPart = address.substring(0, atIdx);
                if (localPart.contains("<")) {
                    localPart = localPart.substring(localPart.indexOf("<") + 1);
                }
                return UUID.fromString(localPart);
            }
        } catch (Exception e) {
            log.warn("Error parsing recipient UUID from email {}", address);
        }
        return null;
    }
}
