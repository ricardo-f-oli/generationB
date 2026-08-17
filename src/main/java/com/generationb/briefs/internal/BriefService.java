package com.generationb.briefs.internal;

import com.generationb.briefs.*;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.ai.AiClient;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service handling campaign briefs workflow, generation, and sharing.
 */
@Service
@Transactional
@Audited
public class BriefService {

    private final BriefRepository briefRepository;
    private final BriefShareRepository briefShareRepository;
    private final BriefMapper briefMapper;
    private final AiClient aiClient;
    private final BrandLookupPort brandLookup;

    public BriefService(BriefRepository briefRepository,
                        BriefShareRepository briefShareRepository,
                        BriefMapper briefMapper,
                        AiClient aiClient,
                        BrandLookupPort brandLookup) {
        this.briefRepository = briefRepository;
        this.briefShareRepository = briefShareRepository;
        this.briefMapper = briefMapper;
        this.aiClient = aiClient;
        this.brandLookup = brandLookup;
    }

    /**
     * Creates a new campaign brief.
     *
     * @param command the properties of the brief to create.
     * @return the created BriefResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BriefResponse createBrief(CreateBriefCommand command) {
        Brief brief = briefMapper.toEntity(command);
        brief.setStatus(BriefStatus.DRAFT);
        brief.setCreatedBy(BrandContext.getCurrentUserId());
        Brief saved = briefRepository.save(brief);
        return briefMapper.toResponse(saved);
    }

    /**
     * Lists campaign briefs for the current brand.
     *
     * @param pageable pagination details.
     * @return a page of briefs.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public Page<BriefResponse> listBriefs(Pageable pageable) {
        return briefRepository.findAllByBrandIdAndDeletedAtIsNull(pageable)
                .map(briefMapper::toResponse);
    }

    /**
     * Retrieves a brief by its ID.
     *
     * @param id the UUID of the brief.
     * @return the matching BriefResponse.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BriefResponse getBrief(UUID id) {
        Brief brief = briefRepository.findByIdAndBrandId(id)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));
        return briefMapper.toResponse(brief);
    }

    /**
     * Updates an existing brief.
     *
     * @param briefId the ID of the brief to update.
     * @param command the update values.
     * @return the updated BriefResponse.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BriefResponse updateBrief(UUID briefId, UpdateBriefCommand command) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));
        briefMapper.updateEntityFromCommand(command, brief);
        Brief saved = briefRepository.save(brief);
        return briefMapper.toResponse(saved);
    }

    /**
     * Requirement #1: turns the campaign inputs into a brief the team can send.
     *
     * <p>Q-B18: this used to concatenate the inputs back together and call the result "AI
     * generated". It now goes through {@link AiClient}; if no key is configured it falls back to
     * the structured assembly below and says so in the document, rather than passing it off.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BriefResponse generateAiBrief(UUID briefId) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> ApiException.notFound("Brief"));

        String brandName = brandLookup.findBrandName(brief.getBrandId()).orElse("the brand");
        String generated = aiClient.generate(briefSystemPrompt(), briefUserPrompt(brief, brandName))
                .orElseGet(() -> structuredBrief(brief, brandName));

        brief.setAiGeneratedContent(generated);
        brief.setStatus(BriefStatus.GENERATED);
        Brief saved = briefRepository.save(brief);
        return briefMapper.toResponse(saved);
    }

    private String briefSystemPrompt() {
        return """
                You write campaign briefs for a UK creator management agency. The reader is a
                creator deciding whether to take the job, so be concrete about what they have to
                make and when.

                Return markdown with exactly these headings, in this order:
                ## The campaign
                ## What we need from you
                ## Key messages
                ## Tone and style
                ## Timings
                ## Practical details

                Rules:
                - British English. No emoji, no exclamation marks, no marketing superlatives.
                - Under 450 words.

                THE ONE RULE THAT MATTERS: this brief is sent to a creator and they will treat
                it as the terms of the job. You may only restate facts given to you in the input.
                You must not invent, infer or give an example of any of the following, even to
                make the brief look more complete:
                  - payment terms, invoicing terms or when a fee is paid
                  - any date, deadline, turnaround time or approval window
                  - social handles, @mentions, hashtags or tracking links
                  - usage rights, exclusivity, licensing or contract terms
                  - deliverables, formats or durations beyond those listed
                  - anything about the creator's audience or past work
                Where one of these is missing from the input, write exactly "To be confirmed".
                A brief that says "To be confirmed" six times is correct; a brief with a
                plausible invented deadline is a problem for the agency.
                """;
    }

    private String briefUserPrompt(Brief brief, String brandName) {
        StringBuilder prompt = new StringBuilder()
                .append("Brand: ").append(brandName).append('\n')
                .append("Campaign: ").append(nz(brief.getCampaignName())).append('\n')
                .append("Goal: ").append(nz(brief.getCampaignGoal())).append('\n')
                .append("Key messages: ").append(nz(brief.getKeyMessages())).append('\n')
                .append("Tone of voice: ").append(brief.getToneOfVoice() == null
                        ? "not specified" : brief.getToneOfVoice()).append('\n');

        if (brief.getDeliverables() != null && !brief.getDeliverables().isEmpty()) {
            prompt.append("Deliverables: ").append(String.join(", ", brief.getDeliverables())).append('\n');
        }
        prompt.append("Budget: ").append(formatBudget(brief.getBudgetMin(), brief.getBudgetMax())).append('\n');
        if (brief.getTimelineStart() != null || brief.getTimelineEnd() != null) {
            prompt.append("Timeline: ")
                    .append(brief.getTimelineStart() == null ? "TBC" : brief.getTimelineStart())
                    .append(" to ")
                    .append(brief.getTimelineEnd() == null ? "TBC" : brief.getTimelineEnd())
                    .append('\n');
        }
        if (brief.getAdditionalNotes() != null && !brief.getAdditionalNotes().isBlank()) {
            prompt.append("Additional notes: ").append(brief.getAdditionalNotes()).append('\n');
        }
        return prompt.toString();
    }

    /** The deterministic version, used when AI drafting is switched off. */
    private String structuredBrief(Brief brief, String brandName) {
        StringBuilder sb = new StringBuilder();
        sb.append("## The campaign\n")
                .append(brandName).append(" — ").append(nz(brief.getCampaignName())).append("\n\n")
                .append(nz(brief.getCampaignGoal())).append("\n\n");

        sb.append("## What we need from you\n");
        if (brief.getDeliverables() != null && !brief.getDeliverables().isEmpty()) {
            brief.getDeliverables().forEach(d -> sb.append("- ").append(d).append('\n'));
        } else {
            sb.append("- To be confirmed\n");
        }
        sb.append('\n');

        sb.append("## Key messages\n").append(nz(brief.getKeyMessages())).append("\n\n");
        sb.append("## Tone and style\n")
                .append(brief.getToneOfVoice() == null ? "To be confirmed" : brief.getToneOfVoice())
                .append("\n\n");

        sb.append("## Timings\n")
                .append(brief.getTimelineStart() == null ? "Start: to be confirmed"
                        : "Start: " + brief.getTimelineStart())
                .append('\n')
                .append(brief.getTimelineEnd() == null ? "End: to be confirmed"
                        : "End: " + brief.getTimelineEnd())
                .append("\n\n");

        sb.append("## Practical details\n")
                .append("Fee: ").append(formatBudget(brief.getBudgetMin(), brief.getBudgetMax()))
                .append('\n');
        if (brief.getAdditionalNotes() != null && !brief.getAdditionalNotes().isBlank()) {
            sb.append(brief.getAdditionalNotes()).append('\n');
        }

        sb.append("\n_Assembled from the campaign inputs. AI drafting is not switched on for this "
                + "environment, so this is a structured draft rather than written copy._");
        return sb.toString();
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "To be confirmed" : value;
    }

    /**
     * Exports a campaign brief as a PDF file.
     *
     * @param briefId the ID of the brief.
     * @return the raw bytes of the generated PDF.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public byte[] exportBriefAsPdf(UUID briefId) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph("Campaign Brief: " + brief.getCampaignName()));
            document.add(new Paragraph("Goal: " + brief.getCampaignGoal()));
            document.add(new Paragraph("Key Messages: " + brief.getKeyMessages()));
            document.add(new Paragraph("Tone of Voice: " + brief.getToneOfVoice()));
            document.add(new Paragraph("Budget: " + formatBudget(brief.getBudgetMin(), brief.getBudgetMax())));
            if (brief.getAiGeneratedContent() != null) {
                document.add(new Paragraph("\nAI Generated Details:\n" + brief.getAiGeneratedContent()));
            }

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate brief PDF", e);
        }
        return baos.toByteArray();
    }

    /**
     * Generates a signed sharing token link for public view.
     *
     * @param briefId the ID of the brief.
     * @return a signed URL path containing the unique share token.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public String getSharedBriefLink(UUID briefId) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));

        BriefShare share = new BriefShare();
        share.setBriefId(briefId);
        share.setToken(UUID.randomUUID().toString());
        share.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        share.setBrandId(brief.getBrandId());

        briefShareRepository.save(share);
        return "/brief/shared/" + share.getToken();
    }

    /**
     * Soft deletes a brief.
     *
     * @param briefId the ID of the brief.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public void deleteBrief(UUID briefId) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));
        brief.setDeletedAt(Instant.now());
        briefRepository.save(brief);
    }

    /**
     * Retrieves a brief by a public share token without security context checks.
     *
     * @param token the sharing token.
     * @return the matching BriefResponse.
     */
    @Transactional(readOnly = true)
    public BriefResponse getSharedBrief(String token) {
        BriefShare share = briefShareRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid share token"));
        if (share.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Share token has expired");
        }
        // The share token carries its own tenant, so downstream brand-scoped queries are bound
        // to the brand that owns the brief rather than to the (anonymous) caller.
        BriefResponse[] holder = new BriefResponse[1];
        BrandContext.runAs(share.getBrandId(), null, () -> {
            Brief brief = briefRepository.findById(share.getBriefId())
                    .orElseThrow(() -> ApiException.notFound("Shared brief"));
            holder[0] = briefMapper.toResponse(brief);
        });
        return holder[0];
    }

    /** Q-J1: the PDF used to print "£null - £null" when no budget was set. */
    private String formatBudget(java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min == null && max == null) {
            return "Not specified";
        }
        if (min != null && max != null) {
            return "£" + min.toPlainString() + " - £" + max.toPlainString();
        }
        return "£" + (min != null ? min.toPlainString() : max.toPlainString());
    }
}
