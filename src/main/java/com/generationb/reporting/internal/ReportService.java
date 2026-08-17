package com.generationb.reporting.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.email.EmailSender;
import com.generationb.reporting.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requirements #51 and #53: report generation at any cadence, and the director sign-off gate.
 */
@Slf4j
@Service
@Transactional
@Audited
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportTemplateRepository templateRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final EmailSender emailSender;

    // ------------------------------------------------------------- lifecycle

    public ReportResponse create(CreateReportCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        if (command.periodEnd().isBefore(command.periodStart())) {
            throw ApiException.badRequest("The period end cannot be before the period start");
        }

        Report report = new Report();
        report.setBrandId(brandId);
        report.setCampaignId(command.campaignId());
        report.setReportType(command.reportType());
        report.setCadence(command.cadence() != null ? command.cadence() : ReportCadence.MONTHLY);
        report.setPeriodStart(command.periodStart());
        report.setPeriodEnd(command.periodEnd());
        report.setStatus(ReportStatus.DRAFT);
        report.setName(command.name() != null && !command.name().isBlank()
                ? command.name().trim()
                : defaultName(command));

        UUID templateId = command.templateId();
        if (templateId == null) {
            templateId = templateRepository.findDefaultFor(command.reportType())
                    .map(ReportTemplate::getId).orElse(null);
        }
        report.setTemplateId(templateId);

        Report saved = reportRepository.save(report);
        return regenerate(saved.getId());
    }

    /** Recomputes and snapshots the metrics. Only allowed while the report is still editable. */
    public ReportResponse regenerate(UUID reportId) {
        Report report = require(reportId);
        if (report.getStatus() == ReportStatus.APPROVED || report.getStatus() == ReportStatus.SENT) {
            throw ApiException.conflict(
                    "This report has been signed off. Duplicate it if you need updated figures.");
        }

        ReportMetrics metrics = metricsService.compute(
                report.getCampaignId(), report.getPeriodStart(), report.getPeriodEnd());
        report.setMetrics(writeJson(metrics));
        return toResponse(reportRepository.save(report));
    }

    /** Requirement #53: Draft → Pending Director Approval, with an email to the director. */
    public ReportResponse submitForApproval(UUID reportId) {
        Report report = require(reportId);
        if (report.getStatus() != ReportStatus.DRAFT && report.getStatus() != ReportStatus.REJECTED) {
            throw ApiException.conflict("Only a draft or rejected report can be submitted for approval");
        }
        report.setStatus(ReportStatus.PENDING_APPROVAL);
        report.setSubmittedBy(BrandContext.getCurrentUserId());
        report.setSubmittedAt(Instant.now());
        report.setRejectionReason(null);

        Report saved = reportRepository.save(report);
        // The brief asks for this to be "triggered by automated email to director".
        notifyDirectors(saved);
        return toResponse(saved);
    }

    /** Requirement #53: only a director or admin may approve. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ReportResponse approve(UUID reportId) {
        Report report = require(reportId);
        if (report.getStatus() != ReportStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("This report is not awaiting approval");
        }
        report.setStatus(ReportStatus.APPROVED);
        report.setApprovedBy(BrandContext.getCurrentUserId());
        report.setApprovedAt(Instant.now());
        return toResponse(reportRepository.save(report));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ReportResponse reject(UUID reportId, String reason) {
        Report report = require(reportId);
        if (report.getStatus() != ReportStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("This report is not awaiting approval");
        }
        report.setStatus(ReportStatus.REJECTED);
        report.setRejectionReason(reason);
        return toResponse(reportRepository.save(report));
    }

    /** Requirement #53: sign-off is required before a report can be shared with a client. */
    public ReportResponse sendToClient(UUID reportId) {
        Report report = require(reportId);
        if (report.getStatus() != ReportStatus.APPROVED) {
            throw ApiException.unprocessable(
                    "This report needs director sign-off before it can go to the client.");
        }
        report.setStatus(ReportStatus.SENT);
        report.setSentAt(Instant.now());
        return toResponse(reportRepository.save(report));
    }

    public void delete(UUID reportId) {
        Report report = require(reportId);
        if (report.getStatus() == ReportStatus.SENT) {
            throw ApiException.conflict("A report that has been sent to a client cannot be deleted");
        }
        report.setDeletedAt(Instant.now());
        reportRepository.save(report);
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Page<ReportResponse> list(ReportStatus status, UUID campaignId, Pageable pageable) {
        return reportRepository.findAllScoped(status, campaignId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID reportId) {
        return toResponse(require(reportId));
    }

    /** Live metrics without persisting a report — powers the preview panel. */
    @Transactional(readOnly = true)
    public ReportMetrics preview(UUID campaignId, LocalDate from, LocalDate to) {
        return metricsService.compute(campaignId, from, to);
    }

    // ------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> listTemplates() {
        return templateRepository.findAllScoped().stream()
                .map(t -> new ReportTemplateResponse(t.getId(), t.getName(), t.getReportType(),
                        t.getSections(), t.isIncludeAffiliate(), t.isDefaultTemplate()))
                .toList();
    }

    public ReportTemplateResponse createTemplate(String name, ReportType type,
                                                 List<String> sections, boolean includeAffiliate) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Template name is required");
        }
        ReportTemplate template = new ReportTemplate();
        template.setBrandId(BrandContext.requireBrandId());
        template.setName(name.trim());
        template.setReportType(type);
        template.setSections(sections != null && !sections.isEmpty()
                ? sections : List.of("summary", "creator_breakdown"));
        template.setIncludeAffiliate(includeAffiliate);

        ReportTemplate saved = templateRepository.save(template);
        return new ReportTemplateResponse(saved.getId(), saved.getName(), saved.getReportType(),
                saved.getSections(), saved.isIncludeAffiliate(), saved.isDefaultTemplate());
    }

    public void deleteTemplate(UUID templateId) {
        ReportTemplate template = templateRepository.findScopedById(templateId)
                .orElseThrow(() -> ApiException.notFound("Report template"));
        template.setDeletedAt(Instant.now());
        templateRepository.save(template);
    }

    // -------------------------------------------------------------- helpers

    Report require(UUID reportId) {
        return reportRepository.findScopedById(reportId)
                .orElseThrow(() -> ApiException.notFound("Report"));
    }

    ReportMetrics readMetrics(Report report) {
        if (report.getMetrics() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(report.getMetrics(), ReportMetrics.class);
        } catch (Exception e) {
            log.warn("Could not read the metrics snapshot for report {}", report.getId(), e);
            return null;
        }
    }

    private String writeJson(ReportMetrics metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise report metrics", e);
        }
    }

    private String defaultName(CreateReportCommand command) {
        String label = switch (command.reportType()) {
            case MONTHLY_SEEDING -> "Monthly seeding";
            case CAMPAIGN_WRAP -> "Campaign wrap";
            case MAILER_CONVERSION -> "Mailer conversion";
        };
        return label + " · " + command.periodStart() + " to " + command.periodEnd();
    }

    private void notifyDirectors(Report report) {
        try {
            emailSender.sendReportApprovalRequest(report.getId(), report.getName());
        } catch (Exception e) {
            // A failed notification must not roll back the submission.
            log.warn("Could not send the approval request for report {}", report.getId(), e);
        }
    }

    ReportResponse toResponse(Report report) {
        return new ReportResponse(
                report.getId(), report.getBrandId(), report.getCampaignId(), report.getTemplateId(),
                report.getName(), report.getReportType(), report.getCadence(),
                report.getPeriodStart(), report.getPeriodEnd(), report.getStatus(),
                readMetrics(report),
                report.getSubmittedBy(), report.getSubmittedAt(),
                report.getApprovedBy(), report.getApprovedAt(), report.getSentAt(),
                report.getRejectionReason(), report.getCreatedAt());
    }
}
