package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement #16: the typed part of "user-definable custom attributes".
 *
 * <p>Two jobs, and the second is the one that gets forgotten. Rejecting "next Tuesday" as a date
 * is obvious. Making sure {@code 03/04/2026} and {@code 2026-04-03} land in the column
 * identically is what stops two creators with the same birthday sorting apart and no filter
 * finding both — and nothing about that failure looks like a bug until someone tries to use it.
 */
class AttributeTypeTest {

    private static final List<String> NO_OPTIONS = List.of();

    private static String normalise(AttributeType type, String value) {
        return type.normalise(value, NO_OPTIONS, false, "Field");
    }

    private static String normalise(AttributeType type, String value, List<String> options) {
        return type.normalise(value, options, false, "Field");
    }

    // =====================================================================

    @Nested
    @DisplayName("Blank values")
    class Blanks {

        @Test
        void clearingAnOptionalAttributeIsAllowed() {
            // Emptying a field is an edit, not a validation failure.
            assertNull(normalise(AttributeType.STRING, ""));
            assertNull(normalise(AttributeType.NUMBER, "   "));
            assertNull(normalise(AttributeType.DATE, null));
        }

        @Test
        void clearingARequiredAttributeIsRefusedByName() {
            ApiException thrown = assertThrows(ApiException.class,
                    () -> AttributeType.STRING.normalise("", NO_OPTIONS, true, "Dress size"));
            // The message names the field, because a form with eight attributes needs to say
            // which one.
            assertTrue(thrown.getMessage().contains("Dress size"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("Numbers")
    class Numbers {

        @Test
        void acceptsAndNormalisesRealNumbers() {
            assertEquals("7", normalise(AttributeType.NUMBER, "007"));
            assertEquals("7", normalise(AttributeType.NUMBER, "7.0"));
            assertEquals("12.5", normalise(AttributeType.NUMBER, " 12.5 "));
            assertEquals("-3", normalise(AttributeType.NUMBER, "-3"));
            // A person typing a follower count uses thousands separators.
            assertEquals("45000", normalise(AttributeType.NUMBER, "45,000"));
        }

        @Test
        void rejectsAnythingElse() {
            assertThrows(ApiException.class, () -> normalise(AttributeType.NUMBER, "about ten"));
            assertThrows(ApiException.class, () -> normalise(AttributeType.NUMBER, "10-12"));
        }
    }

    @Nested
    @DisplayName("Dates")
    class Dates {

        @Test
        void everyAcceptedFormatStoresTheSameString() {
            // The whole point. Three people type a birthday three ways and the column holds one.
            assertEquals("2026-04-03", normalise(AttributeType.DATE, "2026-04-03"));
            assertEquals("2026-04-03", normalise(AttributeType.DATE, "03/04/2026"));
            assertEquals("2026-04-03", normalise(AttributeType.DATE, "3/4/2026"));
            assertEquals("2026-04-03", normalise(AttributeType.DATE, "03-04-2026"));
        }

        @Test
        void ukOrderIsAssumedForSlashedDates() {
            // 03/04 is 3 April to this agency, not 4 March. Getting this backwards sends a gift
            // four weeks late.
            assertEquals("2026-04-03", normalise(AttributeType.DATE, "03/04/2026"));
        }

        @Test
        void rejectsProseAndImpossibleDates() {
            assertThrows(ApiException.class, () -> normalise(AttributeType.DATE, "next Tuesday"));
            assertThrows(ApiException.class, () -> normalise(AttributeType.DATE, "31/02/2026"));
        }
    }

    @Nested
    @DisplayName("Booleans")
    class Booleans {

        @Test
        void acceptsTheWaysPeopleActuallyWriteYesAndNo() {
            for (String yes : new String[] {"true", "TRUE", "yes", "Y", "1"}) {
                assertEquals("true", normalise(AttributeType.BOOLEAN, yes), yes);
            }
            for (String no : new String[] {"false", "no", "N", "0"}) {
                assertEquals("false", normalise(AttributeType.BOOLEAN, no), no);
            }
        }

        @Test
        void rejectsTheAmbiguousMiddle() {
            // "maybe" stored as false is a silently wrong answer.
            assertThrows(ApiException.class, () -> normalise(AttributeType.BOOLEAN, "maybe"));
        }
    }

    @Nested
    @DisplayName("Select lists")
    class Selects {

        private static final List<String> SIZES = List.of("Small", "Medium", "Large");

        @Test
        void matchesCaseInsensitivelyButStoresTheDefinedCasing() {
            // "Vegan" and "vegan" must not become two filter buckets.
            assertEquals("Medium", normalise(AttributeType.SELECT, "medium", SIZES));
            assertEquals("Large", normalise(AttributeType.SELECT, "LARGE", SIZES));
        }

        @Test
        void rejectsAValueOffTheListAndSaysWhatIsOnIt() {
            ApiException thrown = assertThrows(ApiException.class,
                    () -> normalise(AttributeType.SELECT, "Enormous", SIZES));
            assertTrue(thrown.getMessage().contains("Small, Medium, Large"), thrown.getMessage());
        }

        @Test
        void multiSelectTakesSeveralAndDropsDuplicates() {
            assertEquals("Small,Large",
                    normalise(AttributeType.MULTI_SELECT, "small, LARGE, Small", SIZES));
        }

        @Test
        void multiSelectRejectsTheWholeValueIfAnyPartIsWrong() {
            // Silently dropping the bad half would store a subset the user did not choose.
            assertThrows(ApiException.class,
                    () -> normalise(AttributeType.MULTI_SELECT, "Small, Enormous", SIZES));
        }

        @Test
        void aListTypeWithNoListIsAConfigurationError() {
            ApiException thrown = assertThrows(ApiException.class,
                    () -> AttributeType.SELECT.normalise("anything", NO_OPTIONS, false, "Size"));
            assertTrue(thrown.getMessage().contains("Size"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("URLs and emails")
    class Contacts {

        @Test
        void aBareDomainGetsAScheme() {
            // People paste "instagram.com/x" far more often than the full URL.
            assertEquals("https://instagram.com/x", normalise(AttributeType.URL, "instagram.com/x"));
            assertEquals("http://example.com", normalise(AttributeType.URL, "http://example.com"));
        }

        @Test
        void rejectsSomethingWithNoHost() {
            assertThrows(ApiException.class, () -> normalise(AttributeType.URL, "not a url"));
        }

        @Test
        void emailsAreLowercasedSoTheyMatchOnLookup() {
            assertEquals("sam@example.com", normalise(AttributeType.EMAIL, "Sam@Example.com"));
        }

        @Test
        void rejectsSomethingThatIsNotAnAddress() {
            assertThrows(ApiException.class, () -> normalise(AttributeType.EMAIL, "sam@localhost"));
            assertThrows(ApiException.class, () -> normalise(AttributeType.EMAIL, "sam at example"));
        }
    }

    @Nested
    @DisplayName("Parsing the stored type name")
    class Parsing {

        @Test
        void anUnknownStoredTypeReadsAsFreeTextRatherThanThrowing() {
            // Definitions created before this enum existed carry whatever string was passed. A
            // creator screen must not 500 because of a row somebody typed in months ago.
            assertEquals(AttributeType.STRING, AttributeType.of("BANANA"));
            assertEquals(AttributeType.STRING, AttributeType.of(null));
            assertEquals(AttributeType.NUMBER, AttributeType.of("number"));
        }

        @Test
        void definingANewAttributeWithAnUnknownTypeIsRefused() {
            // Strict where it can be: at the point an admin is choosing.
            ApiException thrown =
                    assertThrows(ApiException.class, () -> AttributeType.require("BANANA"));
            assertTrue(thrown.getMessage().contains("not a supported"), thrown.getMessage());
        }
    }
}
