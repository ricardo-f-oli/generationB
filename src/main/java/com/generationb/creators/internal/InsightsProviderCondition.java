package com.generationb.creators.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Decides which {@link com.generationb.creators.CreatorInsightsProvider} is registered.
 *
 * <p>There are two, and no paid vendor behind either:
 *
 * <ul>
 *   <li><b>Platform APIs</b> — Instagram Business Discovery and the YouTube Data API, both free.
 *   <li><b>Mock</b> — generated sample data, so a fresh clone runs with no credentials at all.
 * </ul>
 *
 * <p>The rule:
 *
 * <ul>
 *   <li>{@code insights.provider} set explicitly wins: {@code platform} or {@code mock}.
 *   <li>Unset, with a Meta or YouTube credential present, means the platforms.
 *   <li>Unset with nothing configured means the mock.
 * </ul>
 *
 * <p>A configured credential is taken as intent on its own. Requiring a second switch alongside it
 * once left production quietly running on generated data, with nothing on screen looking wrong.
 */
public class InsightsProviderCondition {

    private static final String PROVIDER = "insights.provider";
    private static final String META_TOKEN = "insights.meta.access-token";
    private static final String YOUTUBE_KEY = "insights.youtube.api-key";

    private InsightsProviderCondition() {
    }

    /** True when creator data should come straight from Meta and Google. */
    static boolean platformApisSelected(ConditionContext context) {
        String provider = trimmed(context.getEnvironment().getProperty(PROVIDER));
        if (provider != null) {
            return "platform".equalsIgnoreCase(provider);
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

    /** Registers the direct platform integration. */
    public static class PlatformApis implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return platformApisSelected(context);
        }
    }

    /**
     * Registers the mock — whenever the platforms are not selected.
     *
     * <p>The two conditions are mutually exclusive and jointly exhaustive, so exactly one provider
     * bean always exists. Two would make the injection point ambiguous; none would fail startup.
     */
    public static class Mock implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !platformApisSelected(context);
        }
    }
}
