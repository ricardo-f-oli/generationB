package com.generationb.outreach.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.ai.AiClient;
import com.generationb.outreach.RecipientStatus;
import com.generationb.shared.FollowUpSuggestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #33: scans un-replied outreach and drafts a follow-up for each.
 *
 * <p>Q-B18: the draft now goes through {@link AiClient} instead of an Anthropic call with a model
 * id that does not exist. Q-G4: the draft is stored on the suggestion, not as a new template.
 */
@Service
@Transactional
@Audited
public class FollowUpSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpSuggestionService.class);

    private final OutreachRecipientRepository recipientRepository;
    private final FollowUpSuggestionRepository suggestionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiClient aiClient;
    private final BrandLookupPort brandLookup;

    @Value("${outreach.follow-up.default-window-days:7}")
    private int defaultWindowDays;

    public FollowUpSuggestionService(
            OutreachRecipientRepository recipientRepository,
            FollowUpSuggestionRepository suggestionRepository,
            ApplicationEventPublisher eventPublisher,
            AiClient aiClient,
            BrandLookupPort brandLookup) {
        this.recipientRepository = recipientRepository;
        this.suggestionRepository = suggestionRepository;
        this.eventPublisher = eventPublisher;
        this.aiClient = aiClient;
        this.brandLookup = brandLookup;
    }

    public record SuggestionRow(
            UUID id, UUID recipientId, String creatorHandle, String creatorFirstName,
            String draftSubject, String draftBody, String status, Instant createdAt) {
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/London")
    public void generateFollowUpSuggestions() {
        Instant cutoffTime = Instant.now().minus(defaultWindowDays, ChronoUnit.DAYS);
        List<OutreachRecipient> eligible =
                recipientRepository.findEligibleForFollowUp(RecipientStatus.SENT, cutoffTime);

        int created = 0;
        for (OutreachRecipient recipient : eligible) {
            try {
                if (suggestionRepository.existsByOutreachRecipientIdAndStatus(
                        recipient.getId(), FollowUpSuggestion.SUGGESTED)) {
                    continue;
                }
                createSuggestion(recipient);
                created++;
            } catch (Exception e) {
                log.error("Could not draft a follow-up for recipient {}", recipient.getId(), e);
            }
        }
        if (created > 0) {
            log.info("Drafted {} follow-up suggestion(s) from {} eligible recipient(s)",
                    created, eligible.size());
        }
    }

    @Transactional(readOnly = true)
    public List<SuggestionRow> list(String status) {
        return suggestionRepository.findAllScoped(status == null || status.isBlank() ? null : status)
                .stream()
                .map(suggestion -> {
                    OutreachRecipient recipient = recipientRepository
                            .findById(suggestion.getOutreachRecipientId()).orElse(null);
                    return new SuggestionRow(suggestion.getId(), suggestion.getOutreachRecipientId(),
                            recipient == null ? "unknown" : recipient.getCreatorHandle(),
                            recipient == null ? null : recipient.getCreatorFirstName(),
                            suggestion.getDraftSubject(), suggestion.getDraftBody(),
                            suggestion.getStatus(), suggestion.getCreatedAt());
                })
                .toList();
    }

    /** Regenerates a draft — the first attempt is a suggestion, not a decision. */
    public SuggestionRow regenerate(UUID suggestionId) {
        FollowUpSuggestion suggestion = requireSuggestion(suggestionId);
        OutreachRecipient recipient = recipientRepository
                .findById(suggestion.getOutreachRecipientId())
                .orElseThrow(() -> ApiException.notFound("Outreach recipient"));

        suggestion.setDraftBody(draftBody(recipient));
        FollowUpSuggestion saved = suggestionRepository.save(suggestion);
        return toRow(saved, recipient);
    }

    public SuggestionRow update(UUID suggestionId, String subject, String body) {
        FollowUpSuggestion suggestion = requireSuggestion(suggestionId);
        if (subject != null) {
            suggestion.setDraftSubject(subject);
        }
        if (body != null) {
            suggestion.setDraftBody(body);
        }
        FollowUpSuggestion saved = suggestionRepository.save(suggestion);
        return toRow(saved, recipientRepository.findById(saved.getOutreachRecipientId()).orElse(null));
    }

    public void dismiss(UUID suggestionId) {
        FollowUpSuggestion suggestion = requireSuggestion(suggestionId);
        suggestion.setStatus(FollowUpSuggestion.DISMISSED);
        suggestionRepository.save(suggestion);
    }

    /** Marks the suggestion as used once the send has gone out. */
    public void markSent(UUID suggestionId) {
        FollowUpSuggestion suggestion = requireSuggestion(suggestionId);
        suggestion.setStatus(FollowUpSuggestion.SENT);
        suggestionRepository.save(suggestion);

        recipientRepository.findById(suggestion.getOutreachRecipientId()).ifPresent(recipient -> {
            recipient.setFollowUpSentAt(Instant.now());
            recipientRepository.save(recipient);
        });
    }

    // -------------------------------------------------------------- helpers

    private void createSuggestion(OutreachRecipient recipient) {
        FollowUpSuggestion suggestion = new FollowUpSuggestion();
        suggestion.setBrandId(recipient.getBrandId());
        suggestion.setOutreachRecipientId(recipient.getId());
        suggestion.setStatus(FollowUpSuggestion.SUGGESTED);
        suggestion.setDraftSubject("Re: " + (recipient.getResolvedSubject() != null
                ? recipient.getResolvedSubject() : "our last message"));
        suggestion.setDraftBody(draftBody(recipient));
        suggestionRepository.save(suggestion);

        recipient.setFollowUpSuggestedAt(Instant.now());
        recipientRepository.save(recipient);

        eventPublisher.publishEvent(new FollowUpSuggestedEvent(
                recipient.getId(), recipient.getCreatorId(), recipient.getBrandId(), Instant.now()));
    }

    private String draftBody(OutreachRecipient recipient) {
        String firstName = recipient.getCreatorFirstName() != null
                ? recipient.getCreatorFirstName() : "there";
        String brandName = brandLookup.findBrandName(recipient.getBrandId()).orElse("the brand");

        long daysSince = recipient.getSentAt() == null ? defaultWindowDays
                : ChronoUnit.DAYS.between(recipient.getSentAt(), Instant.now());

        String systemPrompt = """
                You write short follow-up emails for a UK creator management agency.
                Rules:
                - British English, no exclamation marks, no emoji.
                - Three sentences at most.
                - Warm and low-pressure: they have not replied, and that is fine.
                - Never promise or imply a fee, a payment, a rate, a deadline or any other
                  commercial term. You have not been told them, and this email goes to a
                  creator in the agency's name.
                - Do not invent facts about the creator's audience or past work.
                - Return the email body only: no subject line, no sign-off block, no commentary.
                """;

        String userPrompt = "Creator first name: " + firstName + "\n"
                + "Brand: " + brandName + "\n"
                + "Original subject: " + (recipient.getResolvedSubject() == null
                        ? "a collaboration invitation" : recipient.getResolvedSubject()) + "\n"
                + "Days since we wrote: " + daysSince;

        return aiClient.generate(systemPrompt, userPrompt)
                .orElseGet(() -> "Hi " + firstName + ",\n\n"
                        + "I wanted to float this back to the top of your inbox in case it got buried. "
                        + "We are still keen to work with you on the " + brandName + " collaboration.\n\n"
                        + "No pressure either way — just let me know if it is not one for you.\n\n"
                        + "Best wishes");
    }

    private FollowUpSuggestion requireSuggestion(UUID id) {
        return suggestionRepository.findScopedById(id)
                .orElseThrow(() -> ApiException.notFound("Follow-up suggestion"));
    }

    private SuggestionRow toRow(FollowUpSuggestion suggestion, OutreachRecipient recipient) {
        return new SuggestionRow(suggestion.getId(), suggestion.getOutreachRecipientId(),
                recipient == null ? "unknown" : recipient.getCreatorHandle(),
                recipient == null ? null : recipient.getCreatorFirstName(),
                suggestion.getDraftSubject(), suggestion.getDraftBody(),
                suggestion.getStatus(), suggestion.getCreatedAt());
    }
}
