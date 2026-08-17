package com.generationb.reporting.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.email.EmailSender;
import com.generationb.shared.CoverageMetricsPort;
import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Requirement #52: automated follow-up prompts to creators for missing insights.
 *
 * <p>"Missing" means: this brand sent to the creator, and no coverage has been captured for them
 * in the period. That is the same definition the reconciliation metric uses, so the two agree.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final InsightRequestRepository insightRepository;
    private final CreatorLookupPort creatorLookup;
    private final CoverageMetricsPort coverageMetrics;
    private final EmailSender emailSender;

    public record InsightRow(
            UUID id, UUID creatorId, String handle, String status,
            int chaseCount, Instant lastChasedAt) {
    }

    /**
     * Reconciles the sent list against captured coverage and creates a request row for anyone
     * outstanding. Idempotent — running it twice does not duplicate or re-chase.
     */
    @Transactional
    public List<InsightRow> refresh(UUID campaignId, LocalDate from, LocalDate to) {
        UUID brandId = BrandContext.requireBrandId();

        List<UUID> sentTo = creatorLookup.creatorsSentTo(brandId, campaignId, from, to);
        Set<UUID> posted = new HashSet<>(coverageMetrics.creatorsWhoPosted(brandId, campaignId, from, to));

        for (UUID creatorId : sentTo) {
            Optional<InsightRequest> existing =
                    insightRepository.findByCampaignIdAndCreatorId(campaignId, creatorId);

            if (posted.contains(creatorId)) {
                // They posted: close the request rather than keep chasing.
                existing.filter(r -> !InsightRequest.RECEIVED.equals(r.getStatus()))
                        .ifPresent(r -> {
                            r.setStatus(InsightRequest.RECEIVED);
                            r.setReceivedAt(Instant.now());
                            insightRepository.save(r);
                        });
                continue;
            }

            if (existing.isEmpty()) {
                InsightRequest request = new InsightRequest();
                request.setBrandId(brandId);
                request.setCampaignId(campaignId);
                request.setCreatorId(creatorId);
                request.setStatus(InsightRequest.PENDING);
                insightRepository.save(request);
            }
        }

        return list(campaignId);
    }

    @Transactional(readOnly = true)
    public List<InsightRow> list(UUID campaignId) {
        UUID brandId = BrandContext.requireBrandId();
        List<InsightRequest> requests = insightRepository.findForCampaign(brandId, campaignId);

        Map<UUID, String> handles = creatorLookup
                .profiles(requests.stream().map(InsightRequest::getCreatorId).toList())
                .stream()
                .collect(Collectors.toMap(CreatorLookupPort.CreatorProfile::creatorId,
                        CreatorLookupPort.CreatorProfile::handle, (a, b) -> a));

        return requests.stream()
                .map(r -> new InsightRow(r.getId(), r.getCreatorId(),
                        handles.getOrDefault(r.getCreatorId(), "unknown"),
                        r.getStatus(), r.getChaseCount(), r.getLastChasedAt()))
                .toList();
    }

    /** Chase one creator. */
    @Transactional
    public InsightRow chase(UUID requestId, String campaignName) {
        UUID brandId = BrandContext.requireBrandId();
        InsightRequest request = insightRepository.findById(requestId)
                .filter(r -> r.getBrandId().equals(brandId))
                .orElseThrow(() -> ApiException.notFound("Insight request"));

        sendChase(request, campaignName);
        InsightRequest saved = insightRepository.save(request);

        String handle = creatorLookup.findContact(saved.getCreatorId())
                .map(CreatorLookupPort.CreatorContact::handle).orElse("unknown");
        return new InsightRow(saved.getId(), saved.getCreatorId(), handle,
                saved.getStatus(), saved.getChaseCount(), saved.getLastChasedAt());
    }

    /** Chase everyone still outstanding — the "chase all pending" action. */
    @Transactional
    public int chaseAll(UUID campaignId, String campaignName) {
        UUID brandId = BrandContext.requireBrandId();
        List<InsightRequest> outstanding = insightRepository.findOutstanding(brandId, campaignId);

        for (InsightRequest request : outstanding) {
            sendChase(request, campaignName);
        }
        insightRepository.saveAll(outstanding);
        log.info("Chased {} outstanding insight request(s) for campaign {}", outstanding.size(), campaignId);
        return outstanding.size();
    }

    @Transactional
    public void markReceived(UUID requestId) {
        UUID brandId = BrandContext.requireBrandId();
        InsightRequest request = insightRepository.findById(requestId)
                .filter(r -> r.getBrandId().equals(brandId))
                .orElseThrow(() -> ApiException.notFound("Insight request"));
        request.setStatus(InsightRequest.RECEIVED);
        request.setReceivedAt(Instant.now());
        insightRepository.save(request);
    }

    private void sendChase(InsightRequest request, String campaignName) {
        creatorLookup.findContact(request.getCreatorId()).ifPresent(contact -> {
            if (contact.email() == null || contact.email().isBlank()) {
                log.info("Creator {} has no email; cannot chase", request.getCreatorId());
                return;
            }
            // Requirement #21 still applies: never email someone who opted out.
            if (creatorLookup.isSuppressed(request.getCreatorId())) {
                log.info("Skipping insight chase for suppressed creator {}", request.getCreatorId());
                return;
            }
            emailSender.sendInsightChase(contact.email(), contact.firstName(),
                    campaignName != null ? campaignName : "your recent campaign");
        });

        request.setStatus(InsightRequest.CHASED);
        request.setChaseCount(request.getChaseCount() + 1);
        request.setLastChasedAt(Instant.now());
    }
}
