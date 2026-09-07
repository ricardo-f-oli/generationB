package com.generationb.creators.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which creator-data provider gets registered.
 *
 * <p>Worth its own tests because the failure is invisible. Choosing the mock when the vendor was
 * intended does not throw, does not warn on any screen, and produces plausible numbers — the
 * platform simply reports generated data as though it were a client's real coverage. That is the
 * exact bug this rule was changed to prevent: production ran on the mock for want of a second
 * environment variable nobody knew was needed.
 */
class InsightsProviderConditionTest {

    private static final Condition MODASH = new InsightsProviderCondition.Modash();
    private static final Condition MOCK = new InsightsProviderCondition.Mock();

    /** A context carrying just the two properties the condition reads. */
    private static ConditionContext contextWith(String provider, String apiKey) {
        Map<String, Object> properties = new HashMap<>();
        if (provider != null) {
            properties.put("insights.provider", provider);
        }
        if (apiKey != null) {
            properties.put("insights.modash.api-key", apiKey);
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("test", properties));

        return (ConditionContext) java.lang.reflect.Proxy.newProxyInstance(
                InsightsProviderConditionTest.class.getClassLoader(),
                new Class<?>[] {ConditionContext.class},
                (proxy, method, args) -> {
                    if ("getEnvironment".equals(method.getName())) {
                        return environment;
                    }
                    return null;
                });
    }

    private static void assertVendorSelected(String provider, String apiKey, String why) {
        ConditionContext context = contextWith(provider, apiKey);
        AnnotatedTypeMetadata metadata = null;
        assertTrue(MODASH.matches(context, metadata), why);
        assertFalse(MOCK.matches(context, metadata),
                "both providers would be registered: " + why);
    }

    private static void assertMockSelected(String provider, String apiKey, String why) {
        ConditionContext context = contextWith(provider, apiKey);
        AnnotatedTypeMetadata metadata = null;
        assertTrue(MOCK.matches(context, metadata), why);
        assertFalse(MODASH.matches(context, metadata),
                "both providers would be registered: " + why);
    }

    // =====================================================================

    @Test
    @DisplayName("an API key on its own is enough to use the vendor")
    void aKeyAloneSelectsTheVendor() {
        // The headline rule. Someone who pastes a key into Render and deploys should get the
        // vendor, not generated data plus a log line they will never read.
        assertVendorSelected(null, "SOME-REAL-KEY",
                "a configured key is a clear enough statement of intent");
    }

    @Test
    @DisplayName("no key means the mock, so a fresh clone still runs")
    void noKeyFallsBackToTheMock() {
        assertMockSelected(null, null, "a clone with no vendor account must still start");
    }

    @Test
    @DisplayName("an explicit provider wins over the key in both directions")
    void anExplicitProviderOverrides() {
        // The escape hatch: hold a live key without spending against it yet.
        assertMockSelected("mock", "SOME-REAL-KEY",
                "insights.provider=mock must hold back a live key");

        // And the reverse, so setting it explicitly is never surprising.
        assertVendorSelected("modash", null,
                "insights.provider=modash must select the vendor even with no key, so the "
                        + "startup check reports the missing key rather than silently mocking");
    }

    @Test
    @DisplayName("an empty property counts as unset, not as a choice")
    void emptyPropertiesAreTreatedAsAbsent() {
        // This is the one that would silently break the rule. YAML defaults written as
        // ${INSIGHTS_PROVIDER:} resolve to "" rather than null, and an empty string read as a
        // deliberate choice would match neither "modash" nor the key check.
        assertVendorSelected("", "SOME-REAL-KEY",
                "an unset INSIGHTS_PROVIDER must not veto a configured key");
        assertVendorSelected("   ", "SOME-REAL-KEY",
                "whitespace from a dashboard field must be treated as unset");

        // Likewise an empty key is not a key.
        assertMockSelected(null, "", "an empty MODASH_API_KEY is not a configured key");
        assertMockSelected(null, "   ", "a whitespace key is not a configured key");
    }

    @Test
    @DisplayName("the provider value is matched without regard to case or padding")
    void providerMatchingIsForgiving() {
        // Values typed into a hosting dashboard arrive with surprising shapes.
        assertVendorSelected("MODASH", null, "uppercase");
        assertVendorSelected(" modash ", null, "padded");
        assertMockSelected("MOCK", "KEY", "uppercase mock");
    }

    @Test
    @DisplayName("an unrecognised provider value falls back to the mock rather than the vendor")
    void anUnknownProviderDoesNotSpendMoney() {
        // A typo like "modsah" must not resolve to "spend the client's credits". Failing towards
        // the free option is the safe direction.
        assertMockSelected("modsah", "SOME-REAL-KEY", "a typo must not select a metered vendor");
    }

    @Test
    @DisplayName("exactly one provider is always registered")
    void theTwoConditionsAreStrictInverses() {
        // Both matching would make the injection point ambiguous; neither matching would fail
        // startup on a missing bean. Every combination must resolve to precisely one.
        String[] providers = {null, "", "mock", "modash", "MODASH", "nonsense"};
        String[] keys = {null, "", "KEY"};

        for (String provider : providers) {
            for (String key : keys) {
                ConditionContext context = contextWith(provider, key);
                boolean vendor = MODASH.matches(context, null);
                boolean mock = MOCK.matches(context, null);
                assertNotEquals(vendor, mock,
                        "provider=" + provider + " key=" + key + " registered "
                                + (vendor ? "both" : "neither"));
            }
        }
    }
}
