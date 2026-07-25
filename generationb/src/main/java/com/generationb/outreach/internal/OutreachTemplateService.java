package com.generationb.outreach.internal;

import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.outreach.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
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
    private final RestClient restClient;

    @Value("${anthropic.api-key:MOCK_KEY}")
    private String anthropicApiKey;

    public OutreachTemplateService(OutreachTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
        this.restClient = RestClient.builder().build();
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
     * Generates an outreach template using Anthropic Claude.
     */
    public TemplateResponse generateAiTemplate(GenerateAiTemplateCommand command) {
        String systemPrompt = "You are an outreach specialist for a UK creator management agency. Generate a professional, friendly outreach email template for the given type and brand context. Use tokens {first_name}, {handle}, {brand} where appropriate. Return only the email body, no subject line.";
        String userPrompt = String.format("Outreach Type: %s, Brand Name: %s, Campaign Context: %s, Tone: %s",
            command.type(), command.brandName(), command.campaignContext(), command.tone());

        String generatedBody;
        try {
            // TODO: Swap Anthropic claude-sonnet-4-6 model or endpoint as required
            Map<String, Object> requestBody = Map.of(
                "model", "claude-sonnet-4-6",
                "max_tokens", 1000,
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
                    generatedBody = (String) firstContent.get("text");
                } else {
                    generatedBody = "Hi {first_name},\n\nWe would love to collaborate with {handle} for {brand}!";
                }
            } else {
                generatedBody = "Hi {first_name},\n\nWe would love to collaborate with {handle} for {brand}!";
            }
        } catch (Exception e) {
            log.warn("Anthropic API call failed, falling back to default template. Error: {}", e.getMessage());
            generatedBody = "Hi {first_name},\n\nWe would love to collaborate with {handle} for {brand}!";
        }

        OutreachTemplate template = new OutreachTemplate();
        template.setName("AI Generated - " + command.type());
        template.setType(command.type());
        template.setBrandId(BrandContext.getCurrentBrandId());
        template.setSubjectTemplate("Collaboration Opportunity with " + (command.brandName() != null ? command.brandName() : "{brand}"));
        template.setBodyTemplate(generatedBody);
        template.setAiGenerated(true);
        template.setCreatedBy(BrandContext.getCurrentUserId());

        OutreachTemplate saved = templateRepository.save(template);
        return mapToResponse(saved);
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
