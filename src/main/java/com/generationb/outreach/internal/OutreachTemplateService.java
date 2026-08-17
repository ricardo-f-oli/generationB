package com.generationb.outreach.internal;

import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.ai.AiClient;
import com.generationb.outreach.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing outreach templates and AI template generation.
 */
@Service
@Transactional
@Audited
public class OutreachTemplateService {

    private static final Logger log = LoggerFactory.getLogger(OutreachTemplateService.class);

    private final OutreachTemplateRepository templateRepository;
    private final AiClient aiClient;
    private final BrandLookupPort brandLookup;

    public OutreachTemplateService(OutreachTemplateRepository templateRepository,
                                   AiClient aiClient,
                                   BrandLookupPort brandLookup) {
        this.templateRepository = templateRepository;
        this.aiClient = aiClient;
        this.brandLookup = brandLookup;
    }

    /**
     * Creates a new outreach template.
     */
    public TemplateResponse createTemplate(CreateTemplateCommand command) {
        OutreachTemplate template = new OutreachTemplate();
        template.setName(command.name());
        template.setType(command.type());
        template.setBrandId(command.brandId());
        template.setSubjectTemplate(command.subjectTemplate());
        template.setBodyTemplate(command.bodyTemplate());
        template.setCreatedBy(BrandContext.getCurrentUserId());

        OutreachTemplate saved = templateRepository.save(template);
        return mapToResponse(saved);
    }

    /**
     * Lists templates accessible for a given brandId (including global templates where brand_id IS NULL).
     */
    public List<TemplateResponse> listTemplates(UUID brandId) {
        List<OutreachTemplate> templates;
        if (brandId != null) {
            templates = templateRepository.findActiveTemplatesForBrand(brandId);
        } else {
            templates = templateRepository.findAllActiveTemplates();
        }
        return templates.stream().map(this::mapToResponse).toList();
    }

    /**
     * Gets a template by id.
     */
    public TemplateResponse getTemplate(UUID id) {
        OutreachTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found with id: " + id));
        return mapToResponse(template);
    }

    /**
     * Updates an existing template.
     */
    public TemplateResponse updateTemplate(UUID id, UpdateTemplateCommand command) {
        OutreachTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found with id: " + id));

        template.setName(command.name());
        template.setSubjectTemplate(command.subjectTemplate());
        template.setBodyTemplate(command.bodyTemplate());

        OutreachTemplate updated = templateRepository.save(template);
        return mapToResponse(updated);
    }

    /**
     * Deactivates a template.
     */
    public void deactivateTemplate(UUID id) {
        OutreachTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found with id: " + id));
        template.setActive(false);
        templateRepository.save(template);
    }

    /**
     * Requirement #32: drafts an outreach email.
     *
     * <p>Q-B18: the old version posted to Anthropic with a model id that does not exist, caught
     * the failure, and saved the two-line fallback — so "AI generated" templates were never
     * generated. It now goes through {@link AiClient}, and the fallback is a usable template
     * rather than a stub.
     */
    public TemplateResponse generateAiTemplate(GenerateAiTemplateCommand command) {
        UUID brandId = BrandContext.getCurrentBrandId();
        BrandLookupPort.BrandProfile profile = brandId == null
                ? null : brandLookup.findProfile(brandId).orElse(null);

        String brandName = firstNonBlank(command.brandName(),
                profile == null ? null : profile.name(), "{brand}");
        String tone = firstNonBlank(command.tone(),
                profile == null ? null : profile.toneOfVoice(), "warm and professional");

        String systemPrompt = """
                You write outreach emails for a UK creator management agency.
                Rules:
                - British English, no exclamation marks, no emoji.
                - Keep it under 150 words.
                - Use the merge tokens {first_name}, {handle} and {brand} where a real value belongs.
                - Never promise or imply a fee, a payment, a rate, a deadline, an exclusivity
                  period or any other commercial term. You have not been told them, and this
                  email goes to a creator in the agency's name. Say the details will follow.
                - Do not invent facts about the creator's audience or past work.
                - Return the email body only: no subject line, no sign-off block, no commentary.
                """;

        StringBuilder userPrompt = new StringBuilder()
                .append("Outreach type: ").append(command.type()).append('\n')
                .append("Brand: ").append(brandName).append('\n')
                .append("Tone of voice: ").append(tone).append('\n');
        if (notBlank(command.campaignContext())) {
            userPrompt.append("Campaign context: ").append(command.campaignContext()).append('\n');
        }
        if (profile != null && notBlank(profile.brandGuidelines())) {
            userPrompt.append("Brand guidelines: ").append(profile.brandGuidelines()).append('\n');
        }

        String generatedBody = aiClient.generate(systemPrompt, userPrompt.toString())
                .orElseGet(() -> {
                    log.info("AI drafting unavailable; using the standard {} template", command.type());
                    return fallbackBody(command.type(), brandName, command.campaignContext());
                });

        OutreachTemplate template = new OutreachTemplate();
        template.setName(brandName + " · " + command.type());
        template.setType(command.type());
        template.setBrandId(brandId);
        template.setSubjectTemplate("Collaboration with " + brandName);
        template.setBodyTemplate(generatedBody);
        template.setAiGenerated(true);
        template.setCreatedBy(BrandContext.getCurrentUserId());

        OutreachTemplate saved = templateRepository.save(template);
        return mapToResponse(saved);
    }

    /** Used verbatim when there is no AI key; it has to read as a finished email, not a stub. */
    private String fallbackBody(OutreachType type, String brandName, String context) {
        String opening = switch (type) {
            case GIFTING_CONFIRMATION -> "We have something we would love to send you.";
            case FOLLOW_UP -> "Just circling back on my last note.";
            case RE_ENGAGEMENT -> "It has been a while since we last worked together.";
            case INITIAL_OUTREACH -> "We are working with " + brandName
                    + " and would love to collaborate.";
        };
        return "Hi {first_name},\n\n"
                + opening + "\n\n"
                + "I look after creator partnerships for " + brandName + ", and {handle} felt like "
                + "a natural fit for what we have coming up"
                + (notBlank(context) ? " — " + context : "") + ".\n\n"
                + "If you are interested, reply here and I will send the details across.\n\n"
                + "Best wishes";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private TemplateResponse mapToResponse(OutreachTemplate template) {
        return new TemplateResponse(
            template.getId(),
            template.getName(),
            template.getType(),
            template.getBrandId(),
            template.getSubjectTemplate(),
            template.getBodyTemplate(),
            template.isAiGenerated(),
            template.isActive(),
            template.getCreatedAt()
        );
    }
}
