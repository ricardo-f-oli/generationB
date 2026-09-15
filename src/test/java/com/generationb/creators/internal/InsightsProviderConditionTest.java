package com.generationb.creators.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which creator-data provider gets registered.
 *
 * <p>Worth its own tests because the failure is invisible. Choosing the mock when the platforms
 * were intended does not throw, does not warn on any screen, and produces plausible numbers — the
 * platform simply reports generated data as though it were a client's real coverage.
 */
class InsightsProviderConditionTest {

    private static final Condition PLATFORM = new InsightsProviderCondition.PlatformApis();
    private static final Condition MOCK = new InsightsProviderCondition.Mock();

    private static ConditionContext contextWith(String provider, String metaToken, String youtubeKey) {
        Map<String, Object> properties = new HashMap<>();
        if (provider != null) {
            properties.put("insights.provider", provider);
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

    /** Asserts exactly one of the two conditions matches, and that it is the expected one. */
    private static void assertSelected(Condition expected, ConditionContext context, String why) {
        for (Condition condition : new Condition[] {PLATFORM, MOCK}) {
            boolean matched = condition.matches(context, null);
            if (condition == expected) {
                assertTrue(matched, why);
            } else {
                assertFalse(matched, "a second provider also matched: " + why);
            }
        }
    }

    @Test
    @DisplayName("nothing configured means the mock, so a fresh clone still runs")
    void nothingConfiguredFallsBackToTheMock() {
        assertSelected(MOCK, contextWith(null, null, null), "a clone with no credentials must start");
    }

    @Test
    @DisplayName("a Meta token on its own selects the platform APIs")
    void aMetaTokenSelectsPlatformApis() {
        assertSelected(PLATFORM, contextWith(null, "META-TOKEN", null),
                "configuring Meta is intent to read from Meta");
    }

    @Test
    @DisplayName("a YouTube key on its own does too")
    void aYouTubeKeySelectsPlatformApis() {
        assertSelected(PLATFORM, contextWith(null, null, "YT-KEY"),
                "YouTube alone is a usable configuration");
    }

    @Test
    @DisplayName("an explicit provider wins in both directions")
    void explicitProviderWins() {
        assertSelected(MOCK, contextWith("mock", "META-TOKEN", "YT-KEY"),
                "insights.provider=mock must hold real credentials back");
        assertSelected(PLATFORM, contextWith("platform", null, null),
                "insights.provider=platform must select the platforms even with nothing set, so "
                        + "the startup log reports what is missing rather than silently mocking");
    }

    @Test
    @DisplayName("empty and whitespace values count as unset")
    void emptyValuesAreAbsent() {
        assertSelected(MOCK, contextWith(null, "", "  "),
                "blank credentials must not select a provider that cannot work");
        assertSelected(PLATFORM, contextWith("  ", "META-TOKEN", null),
                "a blank INSIGHTS_PROVIDER must not veto a configured credential");
    }

    @Test
    @DisplayName("the provider value is matched without regard to case or padding")
    void providerMatchingIsForgiving() {
        assertSelected(PLATFORM, contextWith(" PLATFORM ", null, null), "padded uppercase");
        assertSelected(MOCK, contextWith("MOCK", "META", null), "uppercase mock");
    }

    @Test
    @DisplayName("an unrecognised provider value falls back to the mock")
    void anUnknownProviderFallsBackToTheMock() {
        // "modash" included: the vendor is gone, and an old environment variable left behind on
        // a host must not break startup.
        assertSelected(MOCK, contextWith("modash", "META-TOKEN", null), "a retired provider name");
        assertSelected(MOCK, contextWith("platfrom", null, null), "a typo");
    }

    @Test
    @DisplayName("exactly one provider is always registered")
    void theConditionsPartitionEveryCase() {
        String[] providers = {null, "", "mock", "platform", "PLATFORM", "modash", "nonsense"};
        String[] metas = {null, "", "META"};
        String[] youtubes = {null, "", "YT"};

        for (String provider : providers) {
            for (String meta : metas) {
                for (String yt : youtubes) {
                    ConditionContext context = contextWith(provider, meta, yt);
                    int matched = 0;
                    for (Condition condition : new Condition[] {PLATFORM, MOCK}) {
                        if (condition.matches(context, null)) {
                            matched++;
                        }
                    }
                    assertEquals(1, matched, "provider=" + provider + " meta=" + meta
                            + " youtube=" + yt + " matched " + matched + " providers");
                }
            }
        }
    }
}
