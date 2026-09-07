package com.generationb.outreach.internal;

import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.outreach.*;
import com.generationb.shared.CreatorLookupPort;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.shared.OutreachBatchSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service managing outreach campaigns, recipient registration, rendering, and sending.
 */
@Service
@Transactional
@Audited
public class OutreachCampaignService {

    private static final Logger log = LoggerFactory.getLogger(OutreachCampaignService.class);

    private final OutreachCampaignRepository campaignRepository;
    private final OutreachRecipientRepository recipientRepository;
    private final OutreachTemplateRepository templateRepository;
    private final MergeTokenResolver tokenResolver;
    private final SendGridEmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;
    private final CreatorLookupPort creatorLookup;
    private final BrandLookupPort brandLookup;

    public OutreachCampaignService(
            OutreachCampaignRepository campaignRepository,
            OutreachRecipientRepository recipientRepository,
            OutreachTemplateRepository templateRepository,
            MergeTokenResolver tokenResolver,
            SendGridEmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            CreatorLookupPort creatorLookup,
            BrandLookupPort brandLookup) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.tokenResolver = tokenResolver;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.creatorLookup = creatorLookup;
        this.brandLookup = brandLookup;
    }

    /**
     * Creates a new outreach draft campaign.
     */
    public OutreachCampaignResponse createDraft(CreateOutreachDraftCommand command) {
        OutreachCampaign campaign = new OutreachCampaign();
        campaign.setBrandId(BrandContext.getCurrentBrandId());
        campaign.setCampaignId(command.campaignId());
        campaign.setTemplateId(command.templateId());
        campaign.setOutreachType(command.outreachType());
        campaign.setSubject(command.subject());
        campaign.setBody(command.body());
        campaign.setProductName(command.productName());
        campaign.setNoReplyWindowDays(command.noReplyWindowDays() > 0 ? command.noReplyWindowDays() : 7);
        campaign.setStatus(OutreachCampaignStatus.DRAFT);
        campaign.setCreatedBy(BrandContext.getCurrentUserId());

        OutreachCampaign saved = campaignRepository.save(campaign);
        return mapToResponse(saved, 0);
    }

    /**
     * Adds creators as recipients to an outreach campaign draft.
     */
    public OutreachCampaignResponse addRecipients(UUID campaignId, List<UUID> creatorIds) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        for (UUID creatorId : creatorIds) {
            // Q-E13 / requirement #21: never add a suppressed creator to a send list.
            if (creatorLookup.isSuppressed(creatorId)) {
                log.info("Skipping suppressed creator {} for campaign {}", creatorId, campaignId);
                continue;
            }

            CreatorLookupPort.CreatorContact contact = creatorLookup.findContact(creatorId)
                    .orElseThrow(() -> ApiException.notFound("Creator"));
            if (contact.email() == null || contact.email().isBlank()) {
                log.warn("Skipping creator {} because they have no email address", creatorId);
                continue;
            }

            OutreachRecipient recipient = new OutreachRecipient();
            recipient.setOutreachCampaignId(campaign.getId());
            recipient.setBrandId(campaign.getBrandId());
            recipient.setCreatorId(creatorId);
            recipient.setStatus(RecipientStatus.NOT_SENT);
            recipient.setCreatorEmail(contact.email());
            recipient.setCreatorFirstName(contact.firstName());
            recipient.setCreatorHandle(contact.handle());

            recipientRepository.save(recipient);
        }

        List<OutreachRecipient> recipients = recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId);
        return mapToResponse(campaign, recipients.size());
    }

    /**
     * Removes a recipient from an outreach campaign.
     */
    public void removeRecipient(UUID campaignId, UUID recipientId) {
        OutreachRecipient recipient = recipientRepository.findById(recipientId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient not found with id: " + recipientId));
        if (!recipient.getOutreachCampaignId().equals(campaignId)) {
            throw new IllegalArgumentException("Recipient does not belong to campaign: " + campaignId);
        }
        recipient.setDeletedAt(Instant.now());
        recipientRepository.save(recipient);
    }

    /**
     * Resolves all tokens for the given recipient and returns a preview without saving or sending.
     */
    public ResolvedPreviewResponse previewResolved(UUID campaignId, UUID recipientId) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));
        OutreachRecipient recipient = recipientRepository.findById(recipientId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient not found with id: " + recipientId));

        String brandName = brandLookup.findBrandName(campaign.getBrandId()).orElse("");
        String resolvedSubject = tokenResolver.resolveText(campaign.getSubject(), recipient, campaign, brandName);
        String resolvedBody = tokenResolver.resolveText(campaign.getBody(), recipient, campaign, brandName);

        Map<String, String> resolvedTokens = Map.of(
            "first_name", recipient.getCreatorFirstName() != null ? recipient.getCreatorFirstName() : "",
            "handle", recipient.getCreatorHandle() != null ? recipient.getCreatorHandle() : "",
            "brand", brandName,
            "product", campaign.getProductName() != null ? campaign.getProductName() : ""
        );

        return new ResolvedPreviewResponse(resolvedSubject, resolvedBody, resolvedTokens);
    }

    /**
     * Sends the outreach campaign batch immediately.
     */
    public OutreachCampaignResponse sendNow(UUID campaignId) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        if (campaign.getStatus() != OutreachCampaignStatus.DRAFT && campaign.getStatus() != OutreachCampaignStatus.SCHEDULED) {
            throw new IllegalStateException("Campaign status must be DRAFT or SCHEDULED to send");
        }

        List<OutreachRecipient> recipients = recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId);
        if (recipients.isEmpty()) {
            throw new IllegalStateException("Cannot send campaign without recipients");
        }

        campaign.setStatus(OutreachCampaignStatus.SENDING);
        campaignRepository.save(campaign);

        String brandName = brandLookup.findBrandName(campaign.getBrandId()).orElse("");

        for (OutreachRecipient recipient : recipients) {
            recipient.setResolvedSubject(
                    tokenResolver.resolveText(campaign.getSubject(), recipient, campaign, brandName));
            recipient.setResolvedBody(
                    tokenResolver.resolveText(campaign.getBody(), recipient, campaign, brandName));
        }
        recipientRepository.saveAll(recipients);

        // Q-E12: status reflects what actually happened per recipient, instead of everyone
        // being marked SENT before the provider was even called.
        SendGridEmailSender.BatchResult result = emailSender.sendBatch(campaign, recipients);

        for (OutreachRecipient recipient : recipients) {
            if (result.failedRecipientIds().contains(recipient.getId())) {
                recipient.setStatus(RecipientStatus.FAILED);
            } else {
                recipient.setStatus(RecipientStatus.SENT);
                recipient.setSentAt(Instant.now());
                // Requirement #19: send history is finally written.
                creatorLookup.recordSend(recipient.getCreatorId(), recipient.getBrandId(),
                        campaign.getCampaignId(), "OUTREACH", campaign.getProductName());
            }
        }
        recipientRepository.saveAll(recipients);

        campaign.setStatus(result.failedRecipientIds().isEmpty()
                ? OutreachCampaignStatus.SENT
                : OutreachCampaignStatus.PARTIALLY_FAILED);
        campaign.setSentAt(Instant.now());
        OutreachCampaign saved = campaignRepository.save(campaign);

        eventPublisher.publishEvent(new OutreachBatchSentEvent(
            campaign.getId(),
            campaign.getBrandId(),
            recipients.size(),
            Instant.now()
        ));

        return mapToResponse(saved, recipients.size());
    }

    // =====================================================================
    // Requirement #28 interim: the user sends it themselves
    // =====================================================================

    /**
     * One creator's email, personalised and ready for a person to send by hand.
     *
     * @param skipReason set when this creator must not be contacted at all; the address is then
     *                   withheld rather than handed over
     */
    public record ManualSendItem(
            UUID recipientId,
            String creatorHandle,
            String email,
            String subject,
            String body,
            String mailtoUrl,
            String skipReason) {

        public boolean sendable() {
            return skipReason == null;
        }
    }

    public record ManualSendBatch(
            UUID campaignId,
            String campaignName,
            /** False when the platform cannot send for itself, which is why this screen exists. */
            boolean platformCanSend,
            int sendable,
            int skipped,
            List<ManualSendItem> items) {
    }

    /**
     * Prepares a campaign for a person to send from their own mailbox.
     *
     * <p>The sending domain is not authenticated yet (#28), and a domain publishing
     * {@code v=spf1 -all} does not get mail spam-foldered — it gets it rejected. Rather than
     * queue outreach that will bounce, this hands the finished, personalised emails to the user
     * to send from an address that already works.
     *
     * <p>Everything the platform is actually good at still happens: the AI draft, the merge
     * tokens resolved against real data, the opt-out enforcement. Only the SMTP hop moves.
     *
     * <p>Deliberately does <em>not</em> mark anything as sent. The platform has no way of knowing
     * whether the person actually sent them, and a status that says SENT when nothing left the
     * building is worse than no status at all. {@link #markSentManually} is the confirmation.
     */
    @Transactional
    public ManualSendBatch prepareManualSend(UUID campaignId) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ApiException.notFound("Outreach campaign"));

        List<OutreachRecipient> recipients =
                recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId);
        if (recipients.isEmpty()) {
            throw ApiException.unprocessable("This campaign has no recipients yet.");
        }

        String brandName = brandLookup.findBrandName(campaign.getBrandId()).orElse("");
        List<ManualSendItem> items = new ArrayList<>();

        for (OutreachRecipient recipient : recipients) {
            String subject = tokenResolver.resolveText(
                    campaign.getSubject(), recipient, campaign, brandName);
            String body = tokenResolver.resolveText(
                    campaign.getBody(), recipient, campaign, brandName);

            // Resolved copy is stored either way, so the record of what was written survives
            // whether or not it is ever sent.
            recipient.setResolvedSubject(subject);
            recipient.setResolvedBody(body);

            String skipReason = skipReasonFor(recipient);
            items.add(new ManualSendItem(
                    recipient.getId(),
                    recipient.getCreatorHandle(),
                    // A withheld address is withheld properly: copying it out of this screen
                    // would route straight around the opt-out.
                    skipReason == null ? recipient.getCreatorEmail() : null,
                    subject,
                    body,
                    skipReason == null
                            ? mailto(recipient.getCreatorEmail(), subject, body)
                            : null,
                    skipReason));
        }
        recipientRepository.saveAll(recipients);

        int sendable = (int) items.stream().filter(ManualSendItem::sendable).count();
        return new ManualSendBatch(campaignId, campaign.getSubject(),
                emailSender.isConfigured(), sendable, items.size() - sendable, items);
    }

    /**
     * Records that a person sent these by hand.
     *
     * <p>Separate from preparing them because only the user knows whether the mail actually
     * went. Writes send history (#19), so the duplicate flag and the coverage reconciliation
     * still see the contact — which is the thing that would quietly break if manual sends were
     * left off the record.
     */
    @Transactional
    public int markSentManually(UUID campaignId, List<UUID> recipientIds) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ApiException.notFound("Outreach campaign"));

        List<OutreachRecipient> recipients =
                recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId).stream()
                        .filter(r -> recipientIds == null || recipientIds.contains(r.getId()))
                        .toList();

        int marked = 0;
        for (OutreachRecipient recipient : recipients) {
            // Re-checked here too: this is the last point before the contact is recorded, and a
            // creator who opted out since the list was built must not be logged as contacted.
            if (skipReasonFor(recipient) != null || recipient.getStatus() == RecipientStatus.SENT) {
                continue;
            }
            recipient.setStatus(RecipientStatus.SENT);
            recipient.setSentAt(Instant.now());
            creatorLookup.recordSend(recipient.getCreatorId(), recipient.getBrandId(),
                    campaign.getCampaignId(), "OUTREACH_MANUAL", campaign.getProductName());
            marked++;
        }
        recipientRepository.saveAll(recipients);

        if (marked > 0 && campaign.getStatus() != OutreachCampaignStatus.SENT) {
            campaign.setStatus(OutreachCampaignStatus.SENT);
            campaign.setSentAt(Instant.now());
            campaignRepository.save(campaign);
            eventPublisher.publishEvent(new OutreachBatchSentEvent(
                    campaign.getId(), campaign.getBrandId(), marked, Instant.now()));
        }
        log.info("Campaign {}: {} recipient(s) marked as sent by hand", campaignId, marked);
        return marked;
    }

    /**
     * Why this creator must not be contacted, or null if they may be.
     *
     * <p>Checked at send time as well as at add time. Somebody can unsubscribe between being put
     * on a list and the list going out, and on the manual path the consequence is a person
     * copying an address they should not have.
     */
    private String skipReasonFor(OutreachRecipient recipient) {
        if (recipient.getCreatorEmail() == null || recipient.getCreatorEmail().isBlank()) {
            return "No email address on file.";
        }
        if (creatorLookup.isSuppressed(recipient.getCreatorId())) {
            return "Opted out, or has not consented to campaign email.";
        }
        return null;
    }

    /**
     * A mailto: link that opens the user's own mail client with everything filled in.
     *
     * <p>Long bodies are the known limit here — some clients truncate around 2,000 characters —
     * so the screen offers copy-to-clipboard alongside this rather than relying on it.
     */
    private static String mailto(String address, String subject, String body) {
        return "mailto:" + encode(address)
                + "?subject=" + encode(subject)
                + "&body=" + encode(body);
    }

    private static String encode(String value) {
        return value == null ? "" : java.net.URLEncoder.encode(value,
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Schedules send for an outreach campaign.
     */
    public OutreachCampaignResponse scheduleSend(UUID campaignId, Instant scheduledAt) {
        OutreachCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(OutreachCampaignStatus.SCHEDULED);
        OutreachCampaign saved = campaignRepository.save(campaign);

        List<OutreachRecipient> recipients = recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId);
        return mapToResponse(saved, recipients.size());
    }

    /**
     * Scheduled task polling every minute to execute scheduled sends.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processScheduledCampaigns() {
        List<OutreachCampaign> scheduledCampaigns = campaignRepository.findScheduledToRun(
            OutreachCampaignStatus.SCHEDULED,
            Instant.now()
        );
        for (OutreachCampaign campaign : scheduledCampaigns) {
            try {
                sendNow(campaign.getId());
            } catch (Exception e) {
                log.error("Failed to execute scheduled send for campaign {}", campaign.getId(), e);
                campaign.setStatus(OutreachCampaignStatus.PARTIALLY_FAILED);
                campaignRepository.save(campaign);
            }
        }
    }

    /**
     * Retrieves status of all recipients in a campaign.
     */
    public List<RecipientStatusResponse> getRecipientsWithStatus(UUID campaignId) {
        List<OutreachRecipient> recipients = recipientRepository.findByOutreachCampaignIdAndDeletedAtIsNull(campaignId);
        return recipients.stream().map(r -> new RecipientStatusResponse(
            r.getId(),
            r.getCreatorId(),
            r.getCreatorHandle(),
            r.getCreatorFirstName(),
            r.getStatus(),
            r.getSentAt(),
            r.getOpenedAt(),
            r.getRepliedAt()
        )).toList();
    }

    private OutreachCampaignResponse mapToResponse(OutreachCampaign campaign, int recipientCount) {
        return new OutreachCampaignResponse(
            campaign.getId(),
            campaign.getBrandId(),
            campaign.getCampaignId(),
            campaign.getTemplateId(),
            campaign.getSubject(),
            campaign.getBody(),
            campaign.getStatus(),
            campaign.getScheduledAt(),
            campaign.getSentAt(),
            recipientCount
        );
    }
}
