package com.generationb.coverage.internal;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a brand's hashtags and mentions inside a post's caption.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Instagram's hashtag search returns posts with <em>no username</em> — Meta will not say whose
 * post it is through that edge — so a mention found that way cannot be tied to a creator, a
 * campaign or a send list. It also spends one of only 30 unique tags per rolling 7 days, shared
 * across every brand, and it returns no engagement figures.
 *
 * <p>Reading a creator's own posts has none of those problems. Business Discovery is queried by
 * handle, so attribution is exact by construction; the call is already being made for
 * auto-clipping; and the response carries like and comment counts. All that is missing is
 * something to look for in the caption, which is what this does.
 *
 * <p>So the expensive, unattributed route becomes the fallback for genuinely unsolicited posts,
 * and the free, attributed route handles everyone who was actually briefed.
 *
 * <h2>Matching is deliberately forgiving</h2>
 *
 * <p>Creators type tags by hand on a phone. The comparison ignores case, strips accents, and
 * treats {@code #Brand_Summer} and {@code #brandsummer} as the same tag — because a creator who
 * added an underscore has still done what the brief asked, and failing to credit them is a worse
 * error than the occasional loose match.
 *
 * <p>It does not go further than that. Substring matching would make {@code #ad} match
 * {@code #adidas}, so a tag has to match a whole token.
 */
public final class CaptionMatcher {

    /**
     * A hashtag token. Unicode-aware because Instagram accepts non-Latin tags, and a UK agency
     * working with European creators will see them.
     */
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");

    /** A mention. Instagram handles allow dots, so the trailing-full-stop case is handled below. */
    private static final Pattern MENTION = Pattern.compile("@([\\p{L}\\p{N}_.]+)");

    private CaptionMatcher() {
    }

    /**
     * What was found, and why it counts as a match.
     *
     * @param matchedTerm  the brand or campaign term that matched, as configured
     * @param foundAs      the token actually present in the caption, for showing a human
     * @param campaignTag  true when this was the campaign's own tracking tag, which is the
     *                     strongest signal available — it means a briefed creator delivered
     */
    public record Match(String matchedTerm, String foundAs, boolean campaignTag) {
    }

    /** Every hashtag in a caption, normalised for comparison. */
    public static Set<String> hashtagsIn(String caption) {
        return tokens(caption, HASHTAG);
    }

    /** Every @mention in a caption, normalised for comparison. */
    public static Set<String> mentionsIn(String caption) {
        return tokens(caption, MENTION);
    }

    /**
     * Looks for a campaign's tracking tag.
     *
     * <p>Checked before the brand's general tags because it is the more specific answer: a post
     * carrying it was made by someone who was briefed on that campaign, which is exactly what the
     * reconciliation report needs to know.
     */
    public static Optional<Match> matchCampaignTag(String caption, String campaignHashtag) {
        String wanted = normalise(campaignHashtag);
        if (wanted == null || caption == null) {
            return Optional.empty();
        }
        for (String found : rawTokens(caption, HASHTAG)) {
            if (normalise(found).equals(wanted)) {
                return Optional.of(new Match(campaignHashtag, "#" + found, true));
            }
        }
        return Optional.empty();
    }

    /**
     * Looks for any of the brand's monitored hashtags, or its own handle mentioned.
     *
     * @param monitoredHashtags free text as the brand typed it — "#klgifting, katieloxton" — split
     *                          and normalised here rather than expecting it pre-cleaned
     * @param brandHandle       the brand's Instagram handle, so an @mention counts too
     */
    public static Optional<Match> matchBrandTerms(String caption, String monitoredHashtags,
                                                   String brandHandle) {
        if (caption == null) {
            return Optional.empty();
        }

        Set<String> wanted = new LinkedHashSet<>();
        for (String term : splitTerms(monitoredHashtags)) {
            String normalised = normalise(term);
            if (normalised != null) {
                wanted.add(normalised);
            }
        }

        for (String found : rawTokens(caption, HASHTAG)) {
            String normalised = normalise(found);
            if (wanted.contains(normalised)) {
                return Optional.of(new Match(found, "#" + found, false));
            }
        }

        // An @mention of the brand is a mention of the brand, whether or not a tag was used.
        String handle = normalise(brandHandle);
        if (handle != null) {
            for (String found : rawTokens(caption, MENTION)) {
                // A handle at the end of a sentence picks up the full stop.
                String cleaned = normalise(found.replaceAll("\\.+$", ""));
                if (handle.equals(cleaned)) {
                    return Optional.of(new Match(brandHandle, "@" + found, false));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * True when the caption marks the post as an ad or a gift.
     *
     * <p>Not used for attribution — it is a compliance signal. The CAP Code requires a creator to
     * disclose a gifted post, and a campaign where nobody has is worth noticing before the ASA
     * does.
     */
    public static boolean looksDisclosed(String caption) {
        if (caption == null) {
            return false;
        }
        Set<String> tags = hashtagsIn(caption);
        return tags.contains("ad") || tags.contains("gifted") || tags.contains("advert")
                || tags.contains("sponsored") || tags.contains("paidpartnership")
                || tags.contains("adgifted") || tags.contains("giftedbybrand");
    }

    // =====================================================================

    private static Set<String> tokens(String caption, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        for (String token : rawTokens(caption, pattern)) {
            String normalised = normalise(token);
            if (normalised != null) {
                found.add(normalised);
            }
        }
        return found;
    }

    private static Set<String> rawTokens(String caption, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        if (caption == null || caption.isBlank()) {
            return found;
        }
        Matcher matcher = pattern.matcher(caption);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /**
     * Lower case, accents stripped, underscores and dots removed.
     *
     * <p>The underscore rule is the one that earns its keep: creators write {@code #KL_Summer}
     * and {@code #klsummer} interchangeably, and treating them as different tags means quietly
     * failing to credit a post that did exactly what the brief asked for.
     */
    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceFirst("^[#@]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        // NFD splits an accented character into base plus combining mark, so the marks can go.
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.UK)
                .replaceAll("[_.]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** A brand's monitored-hashtags setting is free text: "#klgifting, #katieloxton summer". */
    static Collection<String> splitTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                terms.add(trimmed);
            }
        }
        return terms;
    }
}
