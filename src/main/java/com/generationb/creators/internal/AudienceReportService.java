package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.CreatorDbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The full audience report for a creator: every country and age band CreatorDB measures, not
 * only the four summary figures on the creator row.
 *
 * <p>Reading is free — it returns what was bought before. Buying costs 10 credits and only
 * happens on an explicit request (or when the profile refresh needs demographics anyway), and
 * the answer is stored so the same report is never bought twice by accident.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudienceReportService {

    public record Share(String key, BigDecimal pct) {
    }

    public record Report(String platform, String handle, Instant fetchedAt, String source,
                         List<Share> countries, List<Share> ageBands,
                         BigDecimal femalePct, BigDecimal malePct, Integer averageAge) {
    }

    public record PlatformHandle(String platform, String handle) {
    }

    /** What the profile screen needs to draw the panel: what exists, and what could be bought. */
    public record Overview(boolean available, List<PlatformHandle> platforms, List<Report> reports) {
    }

    private final CreatorRepository creatorRepository;
    private final CreatorAudienceReportRepository reportRepository;
    private final CreatorDbLookup creatorDb;
    private final ObjectMapper objectMapper;

    // =====================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public Overview overview(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));
        List<PlatformHandle> platforms = CreatorDbLookup.targetsOf(creator).stream()
                .map(t -> new PlatformHandle(t.platform(), t.handle()))
                .toList();
        List<Report> reports = reportRepository.findByCreatorIdOrderByPlatformAsc(creatorId).stream()
                .map(stored -> toReport(stored, handleFor(platforms, stored.getPlatform())))
                .flatMap(Optional::stream)
                .toList();
        return new Overview(creatorDb.isEnabled(), platforms, reports);
    }

    /**
     * Buys a fresh report for one platform. When it is the creator's primary platform, the summary
     * figures on the creator row are updated from it too.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public Report refresh(UUID creatorId, String platform) {
        if (!creatorDb.isEnabled()) {
            throw ApiException.unprocessable(
                    "Audience reports need CreatorDB configured on the server (CREATORDB_API_KEY).");
        }
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));
        if (creator.isAnonymised()) {
            throw ApiException.unprocessable("This creator has been anonymised; no report can be bought.");
        }

        List<CreatorDbLookup.Target> targets = CreatorDbLookup.targetsOf(creator);
        String wanted = platform == null || platform.isBlank() ? null : platform.trim().toUpperCase();
        CreatorDbLookup.Target target = targets.stream()
                .filter(t -> wanted == null || t.platform().equals(wanted))
                .findFirst()
                .orElseThrow(() -> ApiException.unprocessable(wanted == null
                        ? "This creator has no handle CreatorDB can look up."
                        : "This creator has no " + wanted.toLowerCase() + " handle on file."));

        CreatorDbClient.Result result = creatorDb.audienceRaw(target);
        if (result.data().isEmpty()) {
            throw ApiException.unprocessable(explain(result, target));
        }
        CreatorDbLookup.AudienceBreakdown breakdown = CreatorDbLookup.breakdown(result.data().get());
        if (breakdown.isEmpty()) {
            throw ApiException.unprocessable("CreatorDB has no audience model for @" + target.handle()
                    + " on " + label(target.platform()) + ". This is common for smaller accounts.");
        }

        CreatorAudienceReport stored = store(creatorId, target.platform(), result.data().get());
        if (target.equals(targets.getFirst())) {
            applySummary(creator, CreatorDbLookup.summarise(target.platform(), breakdown));
            creatorRepository.save(creator);
        }
        return toReport(stored, target.handle()).orElseThrow();
    }

    /**
     * For the profile refresh: buys and stores the report, returning the summary figures. Empty
     * when CreatorDB has nothing, which the refresh treats as "try the next source".
     */
    @Transactional
    public Map<String, Object> fetchSummary(Creator creator, CreatorDbLookup.Target target) {
        CreatorDbClient.Result result = creatorDb.audienceRaw(target);
        if (result.data().isEmpty()) {
            return Map.of();
        }
        CreatorDbLookup.AudienceBreakdown breakdown = CreatorDbLookup.breakdown(result.data().get());
        if (breakdown.isEmpty()) {
            return Map.of();
        }
        store(creator.getId(), target.platform(), result.data().get());
        return CreatorDbLookup.summarise(target.platform(), breakdown);
    }

    // =====================================================================

    private CreatorAudienceReport store(UUID creatorId, String platform, JsonNode data) {
        CreatorAudienceReport report = reportRepository.findByCreatorIdAndPlatform(creatorId, platform)
                .orElseGet(CreatorAudienceReport::new);
        report.setCreatorId(creatorId);
        report.setPlatform(platform);
        report.setSource("CREATORDB");
        report.setReport(data.toString());
        report.setFetchedAt(Instant.now());
        return reportRepository.save(report);
    }

    private String explain(CreatorDbClient.Result result, CreatorDbLookup.Target target) {
        if (result.notIndexed()) {
            // Free, and the only way a creator CreatorDB has never seen ever gets a report.
            Optional<String> status = creatorDb.submit(target);
            return "CreatorDB has not indexed @" + target.handle() + " on " + label(target.platform())
                    + " yet." + (status.filter(s -> !"rejected".equals(s)).isPresent()
                            ? " It has been submitted for indexing (free); try again in a day or two."
                            : "");
        }
        return switch (result.errorCode()) {
            case CreatorDbClient.RESERVE ->
                    "CreatorDB credits are at the reserve set on the server; top up before buying reports.";
            case "INSUFFICIENT_CREDITS", "KEY_SPEND_THRESHOLD_EXCEEDED" ->
                    "CreatorDB credits are spent. Top up in the CreatorDB dashboard.";
            case "", CreatorDbClient.FAILED -> "CreatorDB did not answer; try again shortly.";
            default -> "CreatorDB refused the request (" + result.errorCode() + ").";
        };
    }

    /** Writes the summary onto the creator row, as the profile refresh does. */
    static void applySummary(Creator creator, Map<String, Object> summary) {
        if (summary.get("ukAudiencePct") instanceof Number uk) {
            creator.setUkAudiencePct(BigDecimal.valueOf(uk.doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }
        if (summary.get("topAgeBand") instanceof String age && !age.isBlank()) {
            creator.setAudienceAgeBand(age);
        }
        if (summary.get("genderSplit") instanceof String gender && !gender.isBlank()) {
            creator.setAudienceGenderSplit(gender);
        }
    }

    private Optional<Report> toReport(CreatorAudienceReport stored, String handle) {
        try {
            CreatorDbLookup.AudienceBreakdown b = CreatorDbLookup.breakdown(objectMapper.readTree(stored.getReport()));
            return Optional.of(new Report(stored.getPlatform(), handle, stored.getFetchedAt(), stored.getSource(),
                    b.countries().stream().map(s -> new Share(s.key(), s.pct())).toList(),
                    b.ageBands().stream().map(s -> new Share(s.key(), s.pct())).toList(),
                    b.femalePct(), b.malePct(), b.averageAge()));
        } catch (Exception e) {
            log.warn("Stored audience report {} could not be read: {}", stored.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String handleFor(List<PlatformHandle> platforms, String platform) {
        return platforms.stream().filter(p -> p.platform().equals(platform))
                .map(PlatformHandle::handle).findFirst().orElse(null);
    }

    private static String label(String platform) {
        return switch (platform) {
            case CreatorDbLookup.TIKTOK -> "TikTok";
            case CreatorDbLookup.YOUTUBE -> "YouTube";
            default -> "Instagram";
        };
    }
}
