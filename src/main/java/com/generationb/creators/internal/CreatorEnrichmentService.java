package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.ModashClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Requirement #26: fills in a creator's audience demographics from the vendor, which is what
 * turns the KPI matcher's "not known — audience demographics need the creator-data provider"
 * into a real comparison (#55).
 *
 * <p>Every refresh here costs one Modash credit, so this is deliberately not something a list
 * screen triggers. Three guards keep the bill honest:
 *
 * <ul>
 *   <li>A creator enriched inside {@code insights.enrichment.ttl-days} is skipped. Audience
 *       demographics move over months, not hours.
 *   <li>A bulk refresh takes a batch limit, so "refresh everyone" cannot become 400 credits.
 *   <li>{@link ModashClient} refuses any call that would take the account below its reserve.
 * </ul>
 *
 * <p>A field the vendor did not answer for is left alone rather than blanked. Overwriting a
 * figure someone researched by hand with a null is a worse outcome than a stale one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorEnrichmentService {

    public record EnrichmentResult(
            UUID creatorId,
            String handle,
            boolean refreshed,
            String reason,
            Instant refreshedAt) {

        static EnrichmentResult skipped(Creator creator, String reason) {
            return new EnrichmentResult(creator.getId(), creator.getHandle(), false, reason,
                    creator.getInsightsRefreshedAt());
        }
    }

    /** What the vendor set, written on the creator so provenance is never ambiguous. */
    static final String SOURCE_MODASH = "MODASH";

    private final CreatorRepository creatorRepository;

    /**
     * Absent whenever {@code insights.provider} is not {@code modash}. Enrichment is the one
     * feature with no meaningful mock — inventing a UK audience percentage is exactly the
     * failure this module exists to avoid — so without the vendor it declines instead.
     */
    private final ObjectProvider<ModashCreatorInsightsProvider> modashProvider;

    /** The mock or the real one, whichever is registered. Search degrades; enrichment does not. */
    private final com.generationb.creators.CreatorInsightsProvider insightsProvider;

    @Value("${insights.enrichment.ttl-days:30}")
    private int ttlDays;

    @Value("${insights.enrichment.max-batch:25}")
    private int maxBatch;

    // =====================================================================
    // One creator
    // =====================================================================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public EnrichmentResult enrich(UUID creatorId, boolean force) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));
        return enrichOne(creator, force, requireProvider());
    }

    private EnrichmentResult enrichOne(Creator creator, boolean force,
                                       ModashCreatorInsightsProvider provider) {
        if (creator.isAnonymised()) {
            return EnrichmentResult.skipped(creator,
                    "This creator has been anonymised; their profile is not looked up again.");
        }
        String handle = normalise(creator.getHandle());
        if (handle == null) {
            return EnrichmentResult.skipped(creator, "No handle to look the creator up by.");
        }
        if (!force && isFresh(creator)) {
            return EnrichmentResult.skipped(creator,
                    "Refreshed " + creator.getInsightsRefreshedAt() + "; still within "
                            + ttlDays + " days.");
        }

        Optional<Map<String, Object>> report =
                provider.fetchReport(handle, platformPath(creator.getPrimaryPlatform()));
        if (report.isEmpty()) {
            // Could be an unknown handle, a private account, or an exhausted balance. The client
            // logs which; saying "no data" here rather than inventing one is the point.
            return EnrichmentResult.skipped(creator,
                    "The provider returned nothing for @" + handle + ".");
        }

        apply(creator, report.get());
        creatorRepository.save(creator);
        log.info("Enriched creator {} (@{}) from Modash", creator.getId(), handle);
        return new EnrichmentResult(creator.getId(), creator.getHandle(), true,
                "Audience demographics updated.", creator.getInsightsRefreshedAt());
    }

    /**
     * Copies what the vendor answered onto the creator.
     *
     * <p>Only fields the report actually carried are touched. {@code followersCount} and
     * {@code erPercentage} are refreshed because they are measurements the vendor is better at
     * than we are; {@code niche} is only filled when blank, because a human's classification of
     * a creator is usually better than an interest tag.
     */
    private void apply(Creator creator, Map<String, Object> report) {
        decimal(report.get("ukAudiencePct")).ifPresent(creator::setUkAudiencePct);
        string(report.get("topAgeBand")).ifPresent(creator::setAudienceAgeBand);
        string(report.get("genderSplit")).ifPresent(creator::setAudienceGenderSplit);
        decimal(report.get("credibilityPct"))
                .ifPresent(credibility -> creator.setQualityBand(qualityBand(credibility)));

        integer(report.get("followers"))
                .filter(followers -> followers > 0)
                .ifPresent(followers -> {
                    creator.setFollowersCount(followers);
                    // The stored band was derived from the old count; let it re-derive.
                    creator.setFollowerBand(null);
                });
        decimal(report.get("engagementRatePct")).ifPresent(creator::setErPercentage);

        if (isBlank(creator.getNiche())) {
            string(report.get("niche")).ifPresent(creator::setNiche);
        }
        if (isBlank(creator.getLocation())) {
            string(report.get("creatorCountry")).ifPresent(creator::setLocation);
        }
        string(report.get("externalId")).ifPresent(creator::setInsightsExternalId);

        creator.setInsightsSource(SOURCE_MODASH);
        creator.setInsightsRefreshedAt(Instant.now());
    }

    // =====================================================================
    // Many creators
    // =====================================================================

    /**
     * Refreshes the creators whose figures are oldest, up to {@code limit}.
     *
     * <p>Bounded on purpose. "Refresh the whole database" is a sentence that costs one credit per
     * creator, and the person typing it cannot see the balance.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public List<EnrichmentResult> enrichStalest(int limit, boolean force) {
        ModashCreatorInsightsProvider provider = requireProvider();
        int batch = Math.clamp(limit, 1, maxBatch);

        List<Creator> candidates = creatorRepository.findStalestForEnrichment(
                force ? Instant.now() : staleBefore(),
                org.springframework.data.domain.PageRequest.of(0, batch));

        List<EnrichmentResult> results = new ArrayList<>();
        for (Creator creator : candidates) {
            results.add(enrichOne(creator, force, provider));
        }
        log.info("Bulk enrichment: {} of {} creator(s) refreshed",
                results.stream().filter(EnrichmentResult::refreshed).count(), results.size());
        return results;
    }

    // =====================================================================
    // Discovery (#23) — finding creators who are not in the database yet
    // =====================================================================

    /**
     * Requirement #23: a search that reads a sentence. "Beauty creators in Manchester whose
     * audience skews 25-34" goes to the vendor's semantic index rather than being matched word
     * by word against five columns, which is all our own search can do.
     *
     * <p>Goes through {@link com.generationb.creators.CreatorInsightsProvider} rather than the
     * Modash class directly, so the mock still answers when no key is configured.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public List<Map<String, Object>> discover(String query, String platform, String niche) {
        List<Map<String, Object>> found = insightsProvider.searchCreators(query, platform, niche);
        return found.stream().map(this::markIfKnown).toList();
    }

    /**
     * Flags the results we already hold, so nobody adds a creator twice or pays for a report on
     * someone whose demographics are already on file.
     */
    private Map<String, Object> markIfKnown(Map<String, Object> row) {
        Object handle = row.get("handle");
        if (handle == null) {
            return row;
        }
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(row);
        creatorRepository.findByHandleIgnoreCase(String.valueOf(handle))
                .ifPresent(existing -> {
                    enriched.put("existingCreatorId", existing.getId());
                    enriched.put("alreadyInDatabase", true);
                });
        return enriched;
    }

    /**
     * Requirement #25: who is posting about a competitor.
     *
     * <p>Runs the same mention sweep as the coverage screen but points it at somebody else's
     * hashtag, and — the important part — the results are <em>not</em> written to the coverage
     * log. A competitor's posts are a signal about which creators to approach, not coverage the
     * client earned; filing them as the client's own would inflate every report they receive.
     *
     * <p>Grouped by creator rather than listed by post, because the question being asked is
     * "who should we talk to", not "what was posted".
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public List<Map<String, Object>> competitorMentions(String term, int limit) {
        if (term == null || term.isBlank()) {
            throw ApiException.badRequest("Give a competitor hashtag or handle to search for.");
        }

        Map<String, Map<String, Object>> byCreator = new java.util.LinkedHashMap<>();
        for (Map<String, Object> post : insightsProvider.getMentions(term.trim(), Math.clamp(limit, 1, 60))) {
            Object handle = post.get("handle");
            if (handle == null || String.valueOf(handle).isBlank()) {
                continue;
            }
            Map<String, Object> row = byCreator.computeIfAbsent(String.valueOf(handle), key -> {
                Map<String, Object> created = new java.util.LinkedHashMap<>();
                created.put("handle", key);
                created.put("name", key);
                created.put("platform", post.getOrDefault("platform", "INSTAGRAM"));
                created.put("followers", 0);
                created.put("posts", 0);
                created.put("engagements", 0L);
                created.put("mention", post.get("mention"));
                created.put("latestUrl", post.get("url"));
                return created;
            });
            row.put("posts", (Integer) row.get("posts") + 1);
            row.put("engagements", (Long) row.get("engagements")
                    + asLong(post.get("likes")) + asLong(post.get("comments")));
        }

        return byCreator.values().stream().map(this::markIfKnown).toList();
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Free handle lookup. Confirms a pasted handle resolves to a real account before anything
     * spends a credit on it.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public List<Map<String, Object>> lookup(String query, String platform, int limit) {
        return requireProvider().lookupHandles(query, platform, limit).stream()
                .map(this::markIfKnown)
                .toList();
    }

    /** What the account has left, for the screen that spends it. */
    public Optional<ModashClient.Budget> budget() {
        return modashProvider.getIfAvailable() == null
                ? Optional.empty()
                : requireProvider().budget();
    }

    public boolean isLive() {
        return modashProvider.getIfAvailable() != null;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private ModashCreatorInsightsProvider requireProvider() {
        ModashCreatorInsightsProvider provider = modashProvider.getIfAvailable();
        if (provider == null) {
            throw ApiException.unprocessable(
                    "Audience demographics need the creator-data provider. Set a Modash API key "
                            + "and insights.provider=modash, then try again.");
        }
        return provider;
    }

    private boolean isFresh(Creator creator) {
        return creator.getInsightsRefreshedAt() != null
                && creator.getInsightsRefreshedAt().isAfter(staleBefore());
    }

    private Instant staleBefore() {
        return Instant.now().minus(Duration.ofDays(ttlDays));
    }

    /**
     * Modash's credibility score is the share of the audience that looks like a real person.
     * The bands are the ones the creator screen already displays.
     */
    private static String qualityBand(BigDecimal credibilityPct) {
        if (credibilityPct.compareTo(BigDecimal.valueOf(85)) >= 0) return "HIGH";
        if (credibilityPct.compareTo(BigDecimal.valueOf(70)) >= 0) return "MEDIUM";
        return "LOW";
    }

    private static String platformPath(String platform) {
        if (platform == null) {
            return "instagram";
        }
        return switch (platform.toUpperCase()) {
            case "TIKTOK" -> "tiktok";
            case "YOUTUBE" -> "youtube";
            default -> "instagram";
        };
    }

    private static Optional<BigDecimal> decimal(Object value) {
        if (value instanceof Number number) {
            return Optional.of(BigDecimal.valueOf(number.doubleValue())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    private static Optional<Integer> integer(Object value) {
        return value instanceof Number number ? Optional.of(number.intValue()) : Optional.empty();
    }

    private static Optional<String> string(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String normalise(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        String cleaned = handle.trim().replaceFirst("^@", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
