package com.generationb.creators.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which creator-data providers get registered.
 *
 * <p>Worth its own tests because the failure is invisible. Choosing the mock when a real source
 * was intended does not throw, does not warn on any screen, and produces plausible numbers — the
 * platform simply reports generated data as though it were a client's real coverage.
 */
class InsightsProviderConditionTest {

    private static final Condition CREATORDB = new InsightsProviderCondition.CreatorDb();
    private static final Condition PLATFORM = new InsightsProviderCondition.PlatformApis();
    private static final Condition MOCK = new InsightsProviderCondition.Mock();
    private static final Condition[] ALL = {CREATORDB, PLATFORM, MOCK};

    private static ConditionContext contextWith(String provider, String creatorDbKey,
                                                String metaToken, String youtubeKey) {
        Map<String, Object> properties = new HashMap<>();
        if (provider != null) {
            properties.put("insights.provider", provider);
        }
        if (creatorDbKey != null) {
            properties.put("insights.creatordb.api-key", creatorDbKey);
        }
        if (metaToken != null) {
            properties.put("insights.meta.access-token", metaToken);
        }
        if (youtubeKey != null) {
            properties.put("insights.youtube.api-key", youtubeKey);
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

        return (ConditionContext) java.lang.reflect.Proxy.newProxyInstance(
                InsightsProviderConditionTest.class.getClassLoader(),
                new Class<?>[] {ConditionContext.class},
                (proxy, method, args) -> "getEnvironment".equals(method.getName()) ? environment : null);
    }

    private static ConditionContext contextWith(String provider, String metaToken, String youtubeKey) {
        return contextWith(provider, null, metaToken, youtubeKey);
    }

    private static Set<Condition> registered(ConditionContext context) {
        Set<Condition> matched = new LinkedHashSet<>();
        for (Condition condition : ALL) {
            if (condition.matches(context, null)) {
                matched.add(condition);
            }
        }
        return matched;
    }

    /** Asserts exactly these providers are registered and no others. */
    private static void assertRegistered(ConditionContext context, String why, Condition... expected) {
        assertEquals(Set.of(expected), registered(context), why);
    }

    @Test
    @DisplayName("nothing configured means the mock, so a fresh clone still runs")
    void nothingConfiguredFallsBackToTheMock() {
        assertRegistered(contextWith(null, null, null), "a clone with no credentials must start", MOCK);
    }

    @Test
    @DisplayName("a Meta token on its own selects the platform APIs")
    void aMetaTokenSelectsPlatformApis() {
        assertRegistered(contextWith(null, "META-TOKEN", null),
                "configuring Meta is intent to read from Meta", PLATFORM);
    }

    @Test
    @DisplayName("a YouTube key on its own does too")
    void aYouTubeKeySelectsPlatformApis() {
        assertRegistered(contextWith(null, null, "YT-KEY"),
                "YouTube alone is a usable configuration", PLATFORM);
    }

    @Test
    @DisplayName("a CreatorDB key on its own selects CreatorDB, with no fallback and no mock")
    void aCreatorDbKeySelectsCreatorDb() {
        assertRegistered(contextWith(null, "CDB-KEY", null, null),
                "the mock must never sit behind a real source", CREATORDB);
    }

    @Test
    @DisplayName("CreatorDB with platform credentials keeps the platforms behind it")
    void creatorDbKeepsThePlatformsAsFallback() {
        assertRegistered(contextWith(null, "CDB-KEY", "META-TOKEN", "YT-KEY"),
                "the platforms answer what CreatorDB cannot", CREATORDB, PLATFORM);
        assertRegistered(contextWith("creatordb", "CDB-KEY", null, "YT-KEY"),
                "explicit creatordb keeps the fallback too", CREATORDB, PLATFORM);
    }

    @Test
    @DisplayName("an explicit provider wins in every direction")
    void explicitProviderWins() {
        assertRegistered(contextWith("mock", "CDB-KEY", "META-TOKEN", "YT-KEY"),
                "insights.provider=mock must hold real credentials back", MOCK);
        assertRegistered(contextWith("platform", "CDB-KEY", null, null),
                "insights.provider=platform must not spend CreatorDB credits", PLATFORM);
        assertRegistered(contextWith("platform", null, null),
                "insights.provider=platform must select the platforms even with nothing set, so "
                        + "the startup log reports what is missing rather than silently mocking", PLATFORM);
        assertRegistered(contextWith("creatordb", null, null, null),
                "insights.provider=creatordb without a key still selects it, and it says so at startup",
                CREATORDB);
    }

    @Test
    @DisplayName("empty and whitespace values count as unset")
    void emptyValuesAreAbsent() {
        assertRegistered(contextWith(null, " ", "", "  "),
                "blank credentials must not select a provider that cannot work", MOCK);
        assertRegistered(contextWith("  ", "META-TOKEN", null),
                "a blank INSIGHTS_PROVIDER must not veto a configured credential", PLATFORM);
    }

    @Test
    @DisplayName("the provider value is matched without regard to case or padding")
    void providerMatchingIsForgiving() {
        assertRegistered(contextWith(" PLATFORM ", null, null), "padded uppercase", PLATFORM);
        assertRegistered(contextWith("MOCK", "META", null), "uppercase mock", MOCK);
        assertRegistered(contextWith("CreatorDB", "KEY", null, null), "mixed case", CREATORDB);
    }

    @Test
    @DisplayName("an unrecognised provider value falls back to the mock")
    void anUnknownProviderFallsBackToTheMock() {
        // "modash" included: that vendor is gone, and an old environment variable left behind on
        // a host must not break startup.
        assertRegistered(contextWith("modash", "META-TOKEN", null), "a retired provider name", MOCK);
        assertRegistered(contextWith("platfrom", null, null), "a typo", MOCK);
    }

    @Test
    @DisplayName("there is always exactly one provider to inject, and the mock never shares")
    void theConditionsAlwaysResolveToOneProvider() {
        String[] providers = {null, "", "mock", "platform", "creatordb", "PLATFORM", "modash", "nonsense"};
        String[] keys = {null, "", "CDB"};
        String[] metas = {null, "", "META"};
        String[] youtubes = {null, "", "YT"};

        for (String provider : providers) {
            for (String key : keys) {
                for (String meta : metas) {
                    for (String yt : youtubes) {
                        Set<Condition> matched = registered(contextWith(provider, key, meta, yt));
                        String where = "provider=" + provider + " creatordb=" + key + " meta=" + meta
                                + " youtube=" + yt + " registered " + matched.size();
                        assertFalse(matched.isEmpty(), where);
                        if (matched.size() > 1) {
                            // Two is only ever CreatorDB (@Primary) with the platforms behind it.
                            assertEquals(Set.of(CREATORDB, PLATFORM), matched, where);
                        }
                    }
                }
            }
        }
    }
}
