package com.generationb.briefs.internal;

import com.generationb.briefs.*;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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

    public BriefService(BriefRepository briefRepository, 
                        BriefShareRepository briefShareRepository, 
                        BriefMapper briefMapper) {
        this.briefRepository = briefRepository;
        this.briefShareRepository = briefShareRepository;
        this.briefMapper = briefMapper;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
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
     * Generates simulated AI brief content using the campaign inputs.
     *
     * @param briefId the ID of the brief.
     * @return the brief with simulated AI content populated.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BriefResponse generateAiBrief(UUID briefId) {
        Brief brief = briefRepository.findByIdAndBrandId(briefId)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Generated Campaign Brief: ").append(brief.getCampaignName()).append("\n\n");
        sb.append("## Campaign Goal\n").append(brief.getCampaignGoal()).append("\n\n");
        sb.append("## Core Messages\n").append(brief.getKeyMessages()).append("\n\n");
        sb.append("## Suggested Creative Execution\n");
        sb.append("- Leverage the ").append(brief.getToneOfVoice()).append(" tone of voice across all content.\n");
        if (brief.getDeliverables() != null) {
            sb.append("- Focus deliverables on: ").append(String.join(", ", brief.getDeliverables())).append(".\n\n");
        }
        sb.append("## Additional Guidelines\n").append(brief.getAdditionalNotes());

        // TODO: Replace the following line with actual integration to OpenAI/Anthropic SDK
        String generatedContent = sb.toString();

        brief.setAiGeneratedContent(generatedContent);
        brief.setStatus(BriefStatus.GENERATED);
        Brief saved = briefRepository.save(brief);
        return briefMapper.toResponse(saved);
    }

    /**
     * Exports a campaign brief as a PDF file.
     *
     * @param briefId the ID of the brief.
     * @return the raw bytes of the generated PDF.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
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
            document.add(new Paragraph("Budget: £" + brief.getBudgetMin() + " - £" + brief.getBudgetMax()));
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
        return "/api/public/briefs/share/" + share.getToken();
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
        // Force BrandContext for base queries executed downstream (in case of SpEL constraints)
        BrandContext.setCurrentBrandId(share.getBrandId());
        try {
            Brief brief = briefRepository.findById(share.getBriefId())
                    .orElseThrow(() -> new IllegalArgumentException("Shared brief not found"));
            return briefMapper.toResponse(brief);
        } finally {
            BrandContext.clear();
        }
    }
}
