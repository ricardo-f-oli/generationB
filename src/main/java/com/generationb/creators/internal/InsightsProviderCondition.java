package com.generationb.creators.internal;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Decides which {@link com.generationb.creators.CreatorInsightsProvider} beans are registered.
 *
 * <p>There are three:
 *
 * <ul>
 *   <li><b>CreatorDB</b> — the paid vendor, for discovery, posts and demographics on every
 *       platform. {@code @Primary} when present.
 *   <li><b>Platform APIs</b> — Instagram Business Discovery and the YouTube Data API, both free.
 *       On their own, or behind CreatorDB as its fallback.
 *   <li><b>Mock</b> — generated sample data, so a fresh clone runs with no credentials at all.
 * </ul>
 *
 * <p>The rule:
 *
 * <ul>
 *   <li>{@code insights.provider} set explicitly wins: {@code creatordb}, {@code platform} or
 *       {@code mock}. {@code creatordb} still registers the platform APIs behind it when their
 *       credentials are there.
 *   <li>Unset, a CreatorDB key means CreatorDB, and a Meta or YouTube credential means the
 *       platforms — both when both are configured.
 *   <li>Unset with nothing configured means the mock, and only then.
 * </ul>
 *
 * <p>A configured credential is taken as intent on its own. Requiring a second switch alongside it
 * once left production quietly running on generated data, with nothing on screen looking wrong.
 */
public class InsightsProviderCondition {

    private static final String PROVIDER = "insights.provider";
    private static final String CREATORDB_KEY = "insights.creatordb.api-key";
    private static final String META_TOKEN = "insights.meta.access-token";
    private static final String YOUTUBE_KEY = "insights.youtube.api-key";

    private InsightsProviderCondition() {
    }

    /** True when CreatorDB should answer first. */
    static boolean creatorDbSelected(ConditionContext context) {
        String provider = provider(context);
        if (provider != null) {
            return "creatordb".equals(provider);
        }
        return has(context, CREATORDB_KEY);
    }

    /** True when the platform APIs are registered, either alone or as CreatorDB's fallback. */
    static boolean platformApisSelected(ConditionContext context) {
        String provider = provider(context);
        if ("platform".equals(provider)) {
            return true;
        }
        if (provider != null && !"creatordb".equals(provider)) {
            return false;
        }
        return has(context, META_TOKEN) || has(context, YOUTUBE_KEY);
    }

    private static String provider(ConditionContext context) {
        String provider = trimmed(context.getEnvironment().getProperty(PROVIDER));
        return provider == null ? null : provider.toLowerCase();
    }

    private static boolean has(ConditionContext context, String property) {
        return trimmed(context.getEnvironment().getProperty(property)) != null;
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

    /** Registers the CreatorDB provider. */
    public static class CreatorDb implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return creatorDbSelected(context);
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
     * Registers the mock — whenever neither real provider is.
     *
     * <p>So there is always at least one provider, and when there are two, CreatorDB is
     * {@code @Primary}. The mock is never registered alongside a real one: its invented figures
     * must not be able to reach a real creator through a fallback.
     */
    public static class Mock implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !creatorDbSelected(context) && !platformApisSelected(context);
        }
    }
}
