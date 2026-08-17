package com.generationb.coverage.internal;

import com.generationb.coverage.CoverageDtos.*;
import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Coverage tracking: requirements #11–#15.
 *
 * <p>Q-C1: every read is brand-scoped. The log used to call {@code findAll()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageService {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    /** Open-ended date bounds. Postgres cannot infer the type of a null timestamp parameter. */
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("2999-12-31T23:59:59Z");
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CoverageItemRepository coverageRepository;
    private final CoverageDigestSettingsRepository digestSettingsRepository;
    private final CreatorInsightsProvider insightsProvider;
    private final BrandLookupPort brandLookup;
    private final EmailSender emailSender;

    // =====================================================================
    // Log (#14)
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<CoverageItemResponse> search(String query, String platform, String postType,
                                             UUID campaignId, UUID creatorId, Boolean unsolicited,
                                             Instant from, Instant to, Pageable pageable) {
        BrandContext.requireBrandId();
        return coverageRepository
                .search(blankToNull(query), blankToNull(platform), blankToNull(postType),
                        campaignId, creatorId, unsolicited,
                        from != null ? from : EARLIEST, to != null ? to : LATEST, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public CoverageItemResponse create(CreateCoverageCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        if (command.url() != null && !command.url().isBlank()
                && !coverageRepository.findExistingUrls(brandId, List.of(command.url())).isEmpty()) {
            throw ApiException.conflict("That post is already in the coverage log.");
        }

        CoverageItem item = new CoverageItem();
        item.setBrandId(brandId);
        item.setCampaignId(command.campaignId());
        item.setCreatorId(command.creatorId());
        item.setCreatorHandle(command.creatorHandle().trim().replaceFirst("^@", ""));
        item.setPlatform(orDefault(command.platform(), "INSTAGRAM").toUpperCase());
        item.setPostType(orDefault(command.postType(), "REEL").toUpperCase());
        item.setUrl(blankToNull(command.url()));
        item.setCaption(command.caption());
        item.setViews(nz(command.views()));
        item.setLikes(nz(command.likes()));
        item.setComments(nz(command.comments()));
        item.setShares(command.shares());
        item.setSaves(command.saves());
        item.setImpressions(command.impressions());
        item.setUnsolicited(Boolean.TRUE.equals(command.unsolicited()));
        item.setPostedAt(command.postedAt() != null ? command.postedAt() : Instant.now());
        item.setSource(CoverageItem.MANUAL);

        finish(item, brandId);
        return toResponse(coverageRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        CoverageItem item = coverageRepository.findScopedById(id)
                .orElseThrow(() -> ApiException.notFound("Coverage item"));
        item.setDeletedAt(Instant.now());
        coverageRepository.save(item);
    }

    // =====================================================================
    // Auto-clipping (#11) and clipping names (#12)
    // =====================================================================

    /**
     * Requirement #11: pulls a creator's recent posts from the insights provider and logs
     * anything not already captured.
     *
     * <p>The provider is currently the mock — the Modash contract is not signed — but the shape
     * of the call and the dedupe behaviour are what the real one will use.
     */
    @Transactional
    public ClipResult autoClipCreator(UUID creatorId, String creatorHandle, UUID campaignId) {
        UUID brandId = BrandContext.requireBrandId();
        return ingest(brandId, insightsProvider.getRecentActivity(creatorId),
                creatorId, creatorHandle, campaignId, CoverageItem.AUTO_CLIP, false);
    }

    /**
     * Requirement #11: unsolicited coverage — posts that mention the brand or its hashtags from
     * creators nobody sent product to. Flagged as unsolicited so the report can count them
     * separately.
     */
    @Transactional
    public ClipResult clipBrandMentions(int limit) {
        UUID brandId = BrandContext.requireBrandId();
        BrandLookupPort.BrandProfile profile = brandLookup.findProfile(brandId).orElse(null);

        String term = profile == null ? null
                : (notBlank(profile.monitoredHashtags()) ? profile.monitoredHashtags() : profile.name());
        if (term == null || term.isBlank()) {
            throw ApiException.unprocessable(
                    "Set a brand name or monitored hashtags before searching for mentions.");
        }

        return ingest(brandId, insightsProvider.getMentions(term, Math.min(Math.max(limit, 1), 100)),
                null, null, null, CoverageItem.MENTION, true);
    }

    private ClipResult ingest(UUID brandId, List<Map<String, Object>> posts, UUID creatorId,
                              String creatorHandle, UUID campaignId, String source,
                              boolean unsolicited) {
        if (posts == null || posts.isEmpty()) {
            return new ClipResult(0, 0, List.of());
        }

        List<String> urls = posts.stream()
                .map(p -> asString(p.get("url")))
                .filter(Objects::nonNull)
                .toList();
        Set<String> seen = urls.isEmpty()
                ? Set.of() : new HashSet<>(coverageRepository.findExistingUrls(brandId, urls));

        List<CoverageItemResponse> captured = new ArrayList<>();
        int duplicates = 0;

        for (Map<String, Object> post : posts) {
            String url = asString(post.get("url"));
            if (url != null && !seen.add(url)) {
                duplicates++;
                continue;
            }

            CoverageItem item = new CoverageItem();
            item.setBrandId(brandId);
            item.setCampaignId(campaignId);
            item.setCreatorId(creatorId);
            item.setCreatorHandle(orDefault(
                    creatorHandle != null ? creatorHandle : asString(post.get("handle")), "unknown"));
            item.setPlatform(orDefault(asString(post.get("platform")), "INSTAGRAM").toUpperCase());
            item.setPostType(orDefault(asString(post.get("postType")), "REEL").toUpperCase());
            item.setUrl(url);
            item.setCaption(asString(post.get("caption")));
            item.setExternalId(asString(post.get("id")));
            item.setViews(asLong(post.get("views")));
            item.setLikes(asLong(post.get("likes")));
            item.setComments(asLong(post.get("comments")));
            item.setShares(post.containsKey("shares") ? asLong(post.get("shares")) : null);
            item.setSaves(post.containsKey("saves") ? asLong(post.get("saves")) : null);
            item.setUnsolicited(unsolicited);
            item.setSource(source);
            item.setPostedAt(parseInstant(post.get("postedAt")));

            finish(item, brandId);
            captured.add(toResponse(coverageRepository.save(item)));
        }

        log.info("Clipped {} new item(s) from {} ({} already logged)",
                captured.size(), source, duplicates);
        return new ClipResult(captured.size(), duplicates, captured);
    }

    /** Fills in the derived fields every item needs however it arrived. */
    private void finish(CoverageItem item, UUID brandId) {
        item.setContentForm(CoverageItem.formFor(item.getPostType()));
        if (item.getEr() == null || item.getEr().compareTo(BigDecimal.ZERO) == 0) {
            item.setEr(engagementRate(item));
        }
        item.setStandardizedName(buildClippingName(item, brandId));
    }

    /**
     * Requirement #12: the standardised clipping name. The format is a per-brand setting, so a
     * brand that files assets a particular way gets names that match.
     */
    String buildClippingName(CoverageItem item, UUID brandId) {
        String pattern = digestSettingsRepository.findByBrandId(brandId)
                .map(CoverageDigestSettings::getClippingNamePattern)
                .filter(CoverageService::notBlank)
                .orElse("{brand}_{handle}_{platform}_{type}_{date}");

        String brandName = brandLookup.findBrandName(brandId).orElse("brand");
        LocalDate posted = (item.getPostedAt() == null ? Instant.now() : item.getPostedAt())
                .atZone(LONDON).toLocalDate();

        return pattern
                .replace("{brand}", slug(brandName))
                .replace("{creator}", slug(item.getCreatorHandle()))
                .replace("{handle}", slug(item.getCreatorHandle()))
                .replace("{platform}", slug(item.getPlatform()))
                .replace("{type}", slug(item.getPostType()))
                .replace("{date}", NAME_DATE.format(posted));
    }

    /** Exposed so the settings screen can show a live example of the pattern. */
    @Transactional(readOnly = true)
    public String previewClippingName(String pattern) {
        UUID brandId = BrandContext.requireBrandId();
        CoverageItem sample = new CoverageItem();
        sample.setCreatorHandle("davidtech");
        sample.setPlatform("INSTAGRAM");
        sample.setPostType("REEL");
        sample.setPostedAt(Instant.now());

        if (notBlank(pattern)) {
            String brandName = brandLookup.findBrandName(brandId).orElse("brand");
            return pattern
                    .replace("{brand}", slug(brandName))
                    .replace("{creator}", "davidtech")
                    .replace("{handle}", "davidtech")
                    .replace("{platform}", "instagram")
                    .replace("{type}", "reel")
                    .replace("{date}", NAME_DATE.format(LocalDate.now(LONDON)));
        }
        return buildClippingName(sample, brandId);
    }

    // =====================================================================
    // Digest (#13)
    // =====================================================================

    @Transactional(readOnly = true)
    public DigestSettingsResponse getDigestSettings() {
        UUID brandId = BrandContext.requireBrandId();
        return digestSettingsRepository.findByBrandId(brandId)
                .map(this::toSettingsResponse)
                .orElseGet(() -> toSettingsResponse(new CoverageDigestSettings()));
    }

    @Transactional
    public DigestSettingsResponse updateDigestSettings(UpdateDigestSettingsCommand command) {
        UUID brandId = BrandContext.requireBrandId();
        CoverageDigestSettings settings = digestSettingsRepository.findByBrandId(brandId)
                .orElseGet(() -> {
                    CoverageDigestSettings created = new CoverageDigestSettings();
                    created.setBrandId(brandId);
                    return created;
                });

        if (command.enabled() != null) {
            settings.setEnabled(command.enabled());
        }
        if (notBlank(command.sendTime())) {
            if (!command.sendTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                throw ApiException.badRequest("Send time must look like 08:00.");
            }
            settings.setSendTime(command.sendTime());
        }
        if (command.recipientEmail() != null) {
            settings.setRecipientEmail(blankToNull(command.recipientEmail()));
        }
        if (notBlank(command.clippingNamePattern())) {
            settings.setClippingNamePattern(command.clippingNamePattern().trim());
        }
        if (command.includeUnsolicited() != null) {
            settings.setIncludeUnsolicited(command.includeUnsolicited());
        }

        return toSettingsResponse(digestSettingsRepository.save(settings));
    }

    /**
     * Requirement #13: the 08:00 digest. It used to log "sending digest to X" and send nothing.
     *
     * <p>Runs for every brand that has it switched on, and only when there is something to say —
     * a daily email reading "no new coverage" trains people to ignore it.
     */
    @Transactional
    public int sendDigests() {
        int currentHour = java.time.LocalTime.now(LONDON).getHour();
        int sent = 0;
        for (CoverageDigestSettings settings : digestSettingsRepository.findAll()) {
            if (!settings.isEnabled() || sendHour(settings) != currentHour) {
                continue;
            }
            try {
                if (sendDigestFor(settings)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("Coverage digest failed for brand {}", settings.getBrandId(), e);
            }
        }
        return sent;
    }

    /** The same email the schedule sends, on demand, for the signed-in brand. */
    @Transactional
    public boolean sendDigestNow() {
        UUID brandId = BrandContext.requireBrandId();
        CoverageDigestSettings settings = digestSettingsRepository.findByBrandId(brandId)
                .orElseThrow(() -> ApiException.unprocessable(
                        "Set up the digest before sending one."));
        return sendDigestFor(settings);
    }

    /** The hour of the brand's configured send time; 8am if it is unparseable. */
    private static int sendHour(CoverageDigestSettings settings) {
        try {
            return java.time.LocalTime.parse(settings.getSendTime()).getHour();
        } catch (Exception e) {
            return 8;
        }
    }

    private boolean sendDigestFor(CoverageDigestSettings settings) {
        Instant since = settings.getLastSentAt() != null
                ? settings.getLastSentAt()
                : Instant.now().minus(1, ChronoUnit.DAYS);

        List<CoverageItem> items = coverageRepository.findForDigest(settings.getBrandId(), since).stream()
                .filter(item -> settings.isIncludeUnsolicited() || !item.isUnsolicited())
                .toList();

        if (items.isEmpty()) {
            log.debug("No new coverage for brand {}; digest not sent", settings.getBrandId());
            return false;
        }

        String brandName = brandLookup.findBrandName(settings.getBrandId()).orElse("Generation B");
        String subject = items.size() + " new piece" + (items.size() == 1 ? "" : "s")
                + " of coverage for " + brandName;

        emailSender.sendCoverageDigest(settings.getRecipientEmail(),
                buildDigestHtml(brandName, items), subject);

        settings.setLastSentAt(Instant.now());
        digestSettingsRepository.save(settings);
        return true;
    }

    /** The digest body. Plain HTML: it has to render in Outlook. */
    private String buildDigestHtml(String brandName, List<CoverageItem> items) {
        long totalViews = items.stream().mapToLong(i -> nz(i.getViews())).sum();
        long totalEngagements = items.stream().mapToLong(CoverageItem::engagements).sum();
        long unsolicited = items.stream().filter(CoverageItem::isUnsolicited).count();

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Helvetica,Arial,sans-serif;color:#000;\">");
        html.append("<h2 style=\"margin:0 0 4px;\">").append(escape(brandName)).append(" coverage</h2>");
        html.append("<p style=\"margin:0 0 16px;color:#6B6B6B;font-size:13px;\">")
                .append(items.size()).append(" new post").append(items.size() == 1 ? "" : "s")
                .append(" · ").append(format(totalViews)).append(" views · ")
                .append(format(totalEngagements)).append(" engagements");
        if (unsolicited > 0) {
            html.append(" · ").append(unsolicited).append(" unsolicited");
        }
        html.append("</p>");

        html.append("<table cellpadding=\"8\" cellspacing=\"0\" border=\"0\" width=\"100%\" ")
                .append("style=\"border-collapse:collapse;font-size:13px;\">");
        html.append("<tr style=\"background:#EEEEEE;text-align:left;\">")
                .append("<th>Creator</th><th>Platform</th><th>Views</th><th>Engagements</th><th></th></tr>");

        for (CoverageItem item : items.stream().limit(25).toList()) {
            html.append("<tr style=\"border-bottom:1px solid #D8D8D8;\">")
                    .append("<td>@").append(escape(item.getCreatorHandle()));
            if (item.isUnsolicited()) {
                html.append(" <span style=\"color:#E00008;font-size:11px;\">unsolicited</span>");
            }
            html.append("</td><td>").append(escape(item.getPlatform())).append("</td>")
                    .append("<td>").append(format(nz(item.getViews()))).append("</td>")
                    .append("<td>").append(format(item.engagements())).append("</td>")
                    .append("<td>");
            if (notBlank(item.getUrl())) {
                html.append("<a href=\"").append(escape(item.getUrl())).append("\">View</a>");
            }
            html.append("</td></tr>");
        }
        html.append("</table>");

        if (items.size() > 25) {
            html.append("<p style=\"font-size:12px;color:#6B6B6B;\">and ")
                    .append(items.size() - 25).append(" more in the coverage log.</p>");
        }
        html.append("</div>");
        return html.toString();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private BigDecimal engagementRate(CoverageItem item) {
        long views = nz(item.getViews());
        if (views <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(item.engagements())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(views), 2, RoundingMode.HALF_UP);
    }

    private CoverageItemResponse toResponse(CoverageItem item) {
        return new CoverageItemResponse(
                item.getId(), item.getCampaignId(), item.getCreatorId(), item.getCreatorHandle(),
                item.getPlatform(), item.getPostType(), item.getContentForm(), item.getUrl(),
                item.getCaption(), nz(item.getViews()), nz(item.getLikes()), nz(item.getComments()),
                item.getShares(), item.getSaves(), item.getImpressions(), item.getEr(),
                item.getStandardizedName(), item.isUnsolicited(), item.getSource(),
                item.getPostedAt());
    }

    private DigestSettingsResponse toSettingsResponse(CoverageDigestSettings s) {
        return new DigestSettingsResponse(s.isEnabled(), s.getSendTime(), s.getRecipientEmail(),
                s.getClippingNamePattern(), s.isIncludeUnsolicited(), s.getLastSentAt());
    }

    private static String slug(String value) {
        if (value == null) {
            return "unknown";
        }
        String cleaned = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String format(long value) {
        return String.format(Locale.UK, "%,d", value);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static String orDefault(String value, String fallback) {
        return notBlank(value) ? value.trim() : fallback;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Instant parseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        try {
            return value == null ? Instant.now() : Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
