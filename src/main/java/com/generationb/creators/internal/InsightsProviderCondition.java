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
 *   <li>{@code insights.provider} set explicitly wins, either way. {@code mock} is the escape
 *       hatch for a live key you do not want used yet, and for tests.
 *   <li>Unset, with an API key present, means the vendor. Configuring a key is a clear enough
 *       statement of intent.
 *   <li>Unset with no key means the mock, so a fresh clone still runs.
 * </ul>
 *
 * <p>Whichever way it resolves, {@code ModashCreatorInsightsProvider.announce()} logs which mode
 * is active and the remaining balance at startup. The decision is never silent.
 */
public class InsightsProviderCondition {

    private static final String PROVIDER = "insights.provider";
    private static final String API_KEY = "insights.modash.api-key";

    private InsightsProviderCondition() {
    }

    /** True when the live vendor should be registered. */
    static boolean modashSelected(ConditionContext context) {
        String provider = trimmed(context.getEnvironment().getProperty(PROVIDER));
        if (provider != null) {
            return "modash".equalsIgnoreCase(provider);
        }
        // Unset: a configured key is taken as intent to use it.
        return trimmed(context.getEnvironment().getProperty(API_KEY)) != null;
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

    /** Registers the mock. Exactly the inverse, so precisely one provider always exists. */
    public static class Mock implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !modashSelected(context);
        }
    }
}
