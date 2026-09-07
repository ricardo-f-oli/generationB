package com.generationb.outreach.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.User;
import com.generationb.foundation.UserRepository;
import com.generationb.outreach.RecipientStatus;
import com.generationb.outreach.ThreadDirection;
import com.generationb.outreach.internal.*;
import com.generationb.shared.CreatorFlaggedEvent;
import com.generationb.shared.OutreachReplyReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Requirement #30/#31: SendGrid calls back here with delivery events and inbound replies.
 *
 * <p>Both endpoints are {@code permitAll} in {@link com.generationb.foundation.internal.SecurityConfig}
 * — SendGrid cannot hold a session — so each one authenticates the caller itself:
 *
 * <ul>
 *   <li><b>Events</b> are signed. {@link SendGridSignatureVerifier} checks an ECDSA signature over
 *       the raw body, which is why this takes the body as a String and parses it by hand.
 *   <li><b>Inbound Parse is not signed</b> — SendGrid offers no signature on it. The accepted
 *       practice, and what SendGrid's own documentation suggests, is an unguessable URL. The
 *       token is a path segment compared in constant time.
 * </ul>
 *
 * <p>Both previously accepted anything. The event endpoint's check was
 * {@code !"INVALID_SIG".equals(signature)}; the inbound one had none at all. Either was enough to
 * let a stranger mark a creator as having replied, or file a spam report that suppresses them
 * across every brand.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/sendgrid")
@RequiredArgsConstructor
public class WebhookController {

    private final OutreachRecipientRepository recipientRepository;
    private final EmailThreadRepository emailThreadRepository;
    private final OutreachCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final SendGridEmailSender emailSender;
    private final SendGridSignatureVerifier signatureVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** Where a reply goes when the campaign's owner cannot be resolved. */
    @Value("${outreach.sendgrid.reply-fallback-address:}")
    private String replyFallbackAddress;

    /** The unguessable segment in the Inbound Parse URL. Empty means the endpoint is closed. */
    @Value("${outreach.sendgrid.inbound-token:}")
    private String inboundToken;

    // =====================================================================
    // Delivery events (#31)
    // =====================================================================

    @PostMapping
    public ResponseEntity<Void> handleSendGridEvent(
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false)
            String signature,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false)
            String timestamp,
            @RequestBody(required = false) String rawBody) {

        if (!authorised(signature, timestamp, rawBody)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.ok().build();
        }

        JsonNode events;
        try {
            events = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("SendGrid event payload was not valid JSON");
            return ResponseEntity.badRequest().build();
        }
        if (!events.isArray()) {
            return ResponseEntity.ok().build();
        }

        for (JsonNode event : events) {
            try {
                applyEvent(event);
            } catch (Exception e) {
                // One malformed event must not cost us the rest of the batch: SendGrid retries
                // the whole batch on a non-2xx, which would replay the ones that did work.
                log.warn("Skipped a SendGrid event: {}", e.getClass().getSimpleName());
            }
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Decides whether to trust the call, and says clearly in the log why not.
     *
     * <p>Fails closed. An unconfigured key rejects everything rather than waving it through — the
     * previous behaviour looked like working verification and was not.
     */
    private boolean authorised(String signature, String timestamp, String rawBody) {
        SendGridSignatureVerifier.Result result =
                signatureVerifier.verify(signature, timestamp, rawBody);

        return switch (result) {
            case VALID -> true;
            case NOT_CONFIGURED -> {
                if (signatureVerifier.isAllowingUnsigned()) {
                    log.warn("Accepting an UNSIGNED SendGrid webhook: "
                            + "outreach.sendgrid.webhook-allow-unsigned is on. "
                            + "This must never be set outside local development.");
                    yield true;
                }
                log.error("Rejected a SendGrid webhook: SENDGRID_WEBHOOK_PUBLIC_KEY is not set, "
                        + "so nothing can be verified. Copy the key from the SendGrid Event "
                        + "Webhook settings page.");
                yield false;
            }
            case MISSING -> {
                if (signatureVerifier.isAllowingUnsigned()) {
                    log.warn("Accepting an UNSIGNED SendGrid webhook (allow-unsigned is on)");
                    yield true;
                }
                log.warn("Rejected a SendGrid webhook with no signature headers");
                yield false;
            }
            case STALE -> {
                // Signature was genuine but the timestamp is outside tolerance: either a replay
                // of a captured request or this server's clock has drifted.
                log.warn("Rejected a correctly signed SendGrid webhook as stale. "
                        + "Check this server's clock if it keeps happening.");
                yield false;
            }
            case INVALID -> {
                log.warn("Rejected a SendGrid webhook with an invalid signature");
                yield false;
            }
        };
    }

    private void applyEvent(JsonNode event) {
        String eventType = event.path("event").asText("");
        Optional<OutreachRecipient> found = resolveRecipient(event);
        if (found.isEmpty()) {
            return;
        }
        OutreachRecipient recipient = found.get();

        switch (eventType.toLowerCase()) {
            case "open" -> markOpened(recipient);
            case "click" -> {
                // A click implies an open, but never walk a reply backwards.
                if (recipient.getStatus() != RecipientStatus.REPLIED) {
                    markOpened(recipient);
                }
            }
            case "delivered" -> {
                if (recipient.getStatus() == RecipientStatus.NOT_SENT
                        || recipient.getStatus() == RecipientStatus.SENT) {
                    recipient.setStatus(RecipientStatus.DELIVERED);
                    recipientRepository.save(recipient);
                }
            }
            case "bounce", "dropped" -> {
                recipient.setStatus(RecipientStatus.BOUNCED);
                recipientRepository.save(recipient);
                log.warn("SendGrid reported a {} for recipient {}", eventType, recipient.getId());
            }
            case "spamreport" -> {
                log.warn("Spam report from creator {}", recipient.getCreatorId());
                eventPublisher.publishEvent(new CreatorFlaggedEvent(
                        recipient.getCreatorId(), recipient.getBrandId(),
                        "SPAM_REPORT", Instant.now()));
            }
            case "unsubscribe", "group_unsubscribe" -> {
                log.info("Unsubscribe from creator {}", recipient.getCreatorId());
                recipient.setStatus(RecipientStatus.UNSUBSCRIBED);
                recipientRepository.save(recipient);
                // The flag is what puts them on the global suppression list (#21), so this is
                // enforced on every future send for every brand, not just this campaign.
                eventPublisher.publishEvent(new CreatorFlaggedEvent(
                        recipient.getCreatorId(), recipient.getBrandId(),
                        "UNSUBSCRIBE", Instant.now()));
            }
            default -> { /* processed, deferred and the rest are not acted on */ }
        }
    }

    private void markOpened(OutreachRecipient recipient) {
        if (recipient.getStatus() != RecipientStatus.OPENED) {
            recipient.setStatus(RecipientStatus.OPENED);
        }
        if (recipient.getOpenedAt() == null) {
            recipient.setOpenedAt(Instant.now());
        }
        recipientRepository.save(recipient);
    }

    /** Our own custom arg first, since SendGrid's message id is not unique across retries. */
    private Optional<OutreachRecipient> resolveRecipient(JsonNode event) {
        String recipientId = event.path("outreach_recipient_id").asText(null);
        if (recipientId != null && !recipientId.isBlank()) {
            try {
                Optional<OutreachRecipient> byId =
                        recipientRepository.findById(UUID.fromString(recipientId));
                if (byId.isPresent()) {
                    return byId;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a UUID; fall through to the message id.
            }
        }
        String messageId = event.path("sg_message_id").asText(null);
        return messageId == null || messageId.isBlank()
                ? Optional.empty()
                : recipientRepository.findBySendgridMessageId(messageId);
    }

    // =====================================================================
    // Inbound replies (#30)
    // =====================================================================

    /**
     * Inbound Parse posts the parsed email here. The token in the path is the only thing standing
     * between this endpoint and anyone who can guess a recipient id, so it is compared in constant
     * time and the endpoint stays shut until one is configured.
     */
    @PostMapping(value = "/inbound/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> handleInboundReply(@PathVariable String token,
                                                   MultipartHttpServletRequest request) {
        if (!inboundTokenMatches(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String toAddress = request.getParameter("to");
        if (toAddress == null) {
            log.warn("Inbound email arrived with no 'to' address");
            return ResponseEntity.badRequest().build();
        }

        UUID recipientId = extractRecipientIdFromAddress(toAddress);
        if (recipientId == null) {
            log.warn("Could not read a recipient id out of an inbound address");
            // 200 on purpose: a retry would fail identically and SendGrid would keep trying.
            return ResponseEntity.ok().build();
        }

        Optional<OutreachRecipient> found = recipientRepository.findById(recipientId);
        if (found.isEmpty()) {
            log.warn("Inbound reply for unknown recipient {}", recipientId);
            return ResponseEntity.ok().build();
        }

        OutreachRecipient recipient = found.get();
        String fromAddress = request.getParameter("from");
        String subject = request.getParameter("subject");
        String bodyText = request.getParameter("text");
        String bodyHtml = request.getParameter("html");

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
                recipient.getId(), recipient.getCreatorId(), recipient.getBrandId(),
                recipient.getOutreachCampaignId(), Instant.now()));

        forwardToOwner(recipient, fromAddress, subject, bodyText, bodyHtml);
        return ResponseEntity.ok().build();
    }

    /**
     * Sends the reply on to whoever ran the outreach.
     *
     * <p>This used to be the hard-coded string {@code manager@btheagency.com}, so every creator
     * reply for every brand landed in one mailbox that may not exist.
     */
    private void forwardToOwner(OutreachRecipient recipient, String fromAddress,
                                String subject, String bodyText, String bodyHtml) {
        String owner = campaignRepository.findById(recipient.getOutreachCampaignId())
                .map(OutreachCampaign::getCreatedBy)
                .flatMap(userRepository::findById)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .orElse(blankToNull(replyFallbackAddress));

        if (owner == null) {
            // The reply is safely stored either way; it is the notification that is lost.
            log.warn("Reply on recipient {} stored but not forwarded: the campaign owner has no "
                    + "email and OUTREACH_REPLY_FALLBACK_ADDRESS is unset", recipient.getId());
            return;
        }
        emailSender.forwardReplyToUser(recipient, owner, fromAddress, subject, bodyText, bodyHtml);
    }

    /** Constant time: a timing oracle on a shared secret is still a timing oracle. */
    private boolean inboundTokenMatches(String presented) {
        if (inboundToken == null || inboundToken.isBlank()) {
            log.error("Rejected an inbound reply: OUTREACH_SENDGRID_INBOUND_TOKEN is not set, so "
                    + "the Inbound Parse endpoint is closed.");
            return false;
        }
        if (presented == null) {
            return false;
        }
        boolean matches = MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                inboundToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            log.warn("Rejected an inbound reply with a bad URL token");
        }
        return matches;
    }

    private UUID extractRecipientIdFromAddress(String address) {
        try {
            int at = address.indexOf('@');
            if (at <= 0) {
                return null;
            }
            String localPart = address.substring(0, at);
            if (localPart.contains("<")) {
                localPart = localPart.substring(localPart.indexOf('<') + 1);
            }
            return UUID.fromString(localPart.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
