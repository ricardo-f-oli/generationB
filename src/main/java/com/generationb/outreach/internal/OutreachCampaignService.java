package com.generationb.outreach.internal;

import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.outreach.*;
import com.generationb.shared.OutreachBatchSentEvent;
import com.generationb.shared.ResolveCreatorContactQuery;
import com.generationb.shared.ResolveCreatorContactResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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

    private final Map<UUID, ResolveCreatorContactResponseEvent> contactResponses = new HashMap<>();

    public OutreachCampaignService(
            OutreachCampaignRepository campaignRepository,
            OutreachRecipientRepository recipientRepository,
            OutreachTemplateRepository templateRepository,
            MergeTokenResolver tokenResolver,
            SendGridEmailSender emailSender,
            ApplicationEventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.tokenResolver = tokenResolver;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handleCreatorContactResponse(ResolveCreatorContactResponseEvent event) {
        contactResponses.put(event.requestId(), event);
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
            UUID requestId = UUID.randomUUID();
            eventPublisher.publishEvent(new ResolveCreatorContactQuery(requestId, creatorId, campaign.getBrandId()));

            ResolveCreatorContactResponseEvent response = contactResponses.remove(requestId);

            OutreachRecipient recipient = new OutreachRecipient();
            recipient.setOutreachCampaignId(campaign.getId());
            recipient.setBrandId(campaign.getBrandId());
            recipient.setCreatorId(creatorId);
            recipient.setStatus(RecipientStatus.NOT_SENT);

            if (response != null) {
                recipient.setCreatorEmail(response.creatorEmail());
                recipient.setCreatorFirstName(response.creatorFirstName());
                recipient.setCreatorHandle(response.creatorHandle());
            } else {
                recipient.setCreatorEmail("creator-" + creatorId + "@example.com");
                recipient.setCreatorFirstName("Creator");
                recipient.setCreatorHandle("handle_" + creatorId.toString().substring(0, 6));
            }

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

        String brandName = campaign.getBrandId() != null ? campaign.getBrandId().toString() : "Default Brand";
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

        String brandName = campaign.getBrandId() != null ? campaign.getBrandId().toString() : "Default Brand";

        for (OutreachRecipient recipient : recipients) {
            String resolvedSubject = tokenResolver.resolveText(campaign.getSubject(), recipient, campaign, brandName);
            String resolvedBody = tokenResolver.resolveText(campaign.getBody(), recipient, campaign, brandName);
            recipient.setResolvedSubject(resolvedSubject);
            recipient.setResolvedBody(resolvedBody);
            recipient.setStatus(RecipientStatus.SENT);
            recipient.setSentAt(Instant.now());
        }

        emailSender.sendBatch(campaign, recipients, "user@btheagency.com");

        recipientRepository.saveAll(recipients);

        campaign.setStatus(OutreachCampaignStatus.SENT);
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
