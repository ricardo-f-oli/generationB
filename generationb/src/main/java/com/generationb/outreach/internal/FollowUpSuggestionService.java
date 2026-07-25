package com.generationb.outreach.internal;

import com.generationb.foundation.Audited;
import com.generationb.outreach.OutreachType;
import com.generationb.outreach.RecipientStatus;
import com.generationb.outreach.internal.OutreachTemplate;
import com.generationb.shared.FollowUpSuggestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Service that periodically scans un-replied outreach recipients and suggests AI follow-up drafts.
 */
@Service
@Transactional
@Audited
public class FollowUpSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpSuggestionService.class);

    private final OutreachRecipientRepository recipientRepository;
    private final OutreachTemplateRepository templateRepository;
    private final FollowUpSuggestionRepository suggestionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;

    @Value("${anthropic.api-key:MOCK_KEY}")
    private String anthropicApiKey;

    @Value("${outreach.follow-up.default-window-days:7}")
    private int defaultWindowDays;

    public FollowUpSuggestionService(
            OutreachRecipientRepository recipientRepository,
            OutreachTemplateRepository templateRepository,
            FollowUpSuggestionRepository suggestionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.suggestionRepository = suggestionRepository;
        this.eventPublisher = eventPublisher;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Daily scheduled task running at 08:00 Europe/London.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/London")
    public void generateFollowUpSuggestions() {
        log.info("Starting daily follow-up suggestion generator...");
        Instant cutoffTime = Instant.now().minus(defaultWindowDays, ChronoUnit.DAYS);

        List<OutreachRecipient> eligibleRecipients = recipientRepository.findEligibleForFollowUp(
            RecipientStatus.SENT,
            cutoffTime
        );

        log.info("Found {} recipients eligible for follow-up suggestion.", eligibleRecipients.size());

        for (OutreachRecipient recipient : eligibleRecipients) {
            try {
                String aiDraft = generateFollowUpDraft(recipient);

                OutreachTemplate template = new OutreachTemplate();
                template.setName("Follow-up Suggestion for " + recipient.getCreatorHandle());
                template.setType(OutreachType.FOLLOW_UP);
                template.setBrandId(recipient.getBrandId());
                template.setSubjectTemplate("Following up: " + (recipient.getResolvedSubject() != null ? recipient.getResolvedSubject() : "Our previous message"));
                template.setBodyTemplate(aiDraft);
                template.setAiGenerated(true);

                OutreachTemplate savedTemplate = templateRepository.save(template);

                FollowUpSuggestion suggestion = new FollowUpSuggestion();
                suggestion.setOutreachRecipientId(recipient.getId());
                suggestion.setTemplateId(savedTemplate.getId());
                suggestion.setBrandId(recipient.getBrandId());
                suggestionRepository.save(suggestion);

                recipient.setFollowUpSuggestedAt(Instant.now());
                recipientRepository.save(recipient);

                eventPublisher.publishEvent(new FollowUpSuggestedEvent(
                    recipient.getId(),
                    recipient.getCreatorId(),
                    recipient.getBrandId(),
                    Instant.now()
                ));
            } catch (Exception e) {
                log.error("Failed to generate follow up suggestion for recipient {}", recipient.getId(), e);
            }
        }
    }

    private String generateFollowUpDraft(OutreachRecipient recipient) {
        String systemPrompt = "Generate a brief, friendly follow-up email for a creator outreach that has not received a reply. Keep it under 3 sentences. Reference the original campaign context.";
        String userPrompt = "Creator Name: " + recipient.getCreatorFirstName() + ", Original Subject: " + recipient.getResolvedSubject();

        try {
            Map<String, Object> requestBody = Map.of(
                "model", "claude-sonnet-4-6",
                "max_tokens", 500,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );

            Map response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

            if (response != null && response.containsKey("content")) {
                List contentList = (List) response.get("content");
                if (!contentList.isEmpty()) {
                    Map firstContent = (Map) contentList.get(0);
                    return (String) firstContent.get("text");
                }
            }
        } catch (Exception e) {
            log.warn("Anthropic API call failed for follow-up draft, fallback used. Error: {}", e.getMessage());
        }

        return "Hi " + (recipient.getCreatorFirstName() != null ? recipient.getCreatorFirstName() : "{first_name}") +
            ", just checking in on my previous message! Would love to hear your thoughts on collaborating.";
    }
}
