package com.generationb.creators.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Decides which {@link com.generationb.creators.CreatorInsightsProvider} is registered.
 *
 * <h2>Why this is not just {@code @ConditionalOnProperty}</h2>
 *
 * <p>It used to be, and it needed two variables set together: {@code INSIGHTS_PROVIDER=modash}
 * and {@code MODASH_API_KEY}. Setting only the key left the platform running on generated data,
 * silently — the log said {@code [MOCK MODASH]} and nothing on screen looked wrong. That is a bad
 * trade: the reason for the second switch was to allow installing a key without going live, which
 * is a thing you do roughly once, against a cost that is paid every deploy by someone who
 * reasonably assumed a configured key meant a configured vendor.
 *
 * <p>So the rule is now:
 *
 * <ul>
 *   <li>{@code insights.provider} set explicitly wins: {@code modash}, {@code platform} or
 *       {@code mock}. The last is the escape hatch for a live key you do not want used yet, and
 *       for tests.
 *   <li>Unset, with a vendor key present, means the vendor.
 *   <li>Unset, with a Meta or YouTube credential present, means the platforms directly.
 *   <li>Unset with nothing configured means the mock, so a fresh clone still runs.
 * </ul>
 *
 * <p>The vendor is checked first because it answers strictly more questions than the platform
 * APIs do — if both are configured, the one being paid for is the one that was meant.
 *
 * <p>Whichever way it resolves, {@code ModashCreatorInsightsProvider.announce()} logs which mode
 * is active and the remaining balance at startup. The decision is never silent.
 */
public class InsightsProviderCondition {

    private static final String PROVIDER = "insights.provider";
    private static final String API_KEY = "insights.modash.api-key";
    private static final String META_TOKEN = "insights.meta.access-token";
    private static final String YOUTUBE_KEY = "insights.youtube.api-key";

    private InsightsProviderCondition() {
    }

    /** True when the paid data vendor should be registered. */
    static boolean modashSelected(ConditionContext context) {
        String provider = trimmed(context.getEnvironment().getProperty(PROVIDER));
        if (provider != null) {
            return "modash".equalsIgnoreCase(provider);
        }
        // Unset: a configured vendor key is taken as intent to use it. Checked before the
        // platform APIs because a paid vendor answers strictly more questions — if somebody has
        // both configured, the one they are paying for is the one they meant.
        return trimmed(context.getEnvironment().getProperty(API_KEY)) != null;
    }

    /**
     * True when creator data should come straight from Meta, Google and TikTok.
     *
     * <p>Same rule as the vendor: an explicit {@code insights.provider=platform} wins, otherwise
     * having configured either platform credential is taken as intent — provided no vendor key
     * is set, since that takes precedence.
     */
    static boolean platformApisSelected(ConditionContext context) {
        String provider = trimmed(context.getEnvironment().getProperty(PROVIDER));
        if (provider != null) {
            return "platform".equalsIgnoreCase(provider);
        }
        if (modashSelected(context)) {
            return false;
        }
        return trimmed(context.getEnvironment().getProperty(META_TOKEN)) != null
                || trimmed(context.getEnvironment().getProperty(YOUTUBE_KEY)) != null;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        // An empty property is the same as an absent one — YAML defaults like ${VAR:} resolve
        // to "" rather than null, and treating that as "set" would break the whole rule.
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Registers the live vendor. */
    public static class Modash implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return modashSelected(context);
        }
    }

    /** Registers the direct platform integration. */
    public static class PlatformApis implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return platformApisSelected(context);
        }
    }

    /**
     * Registers the mock — whenever neither real source is selected.
     *
     * <p>The three conditions are mutually exclusive and jointly exhaustive, so exactly one
     * provider bean always exists. Two would make the injection point ambiguous; none would fail
     * startup on a missing bean.
     */
    public static class Mock implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !modashSelected(context) && !platformApisSelected(context);
        }
    }
}
