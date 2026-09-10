package com.generationb.coverage.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caption matching, which is how a post gets attributed without paying for a hashtag lookup.
 *
 * <p>Worth testing hard because both failure directions are silent and both are expensive. A
 * missed match means a creator who did exactly what the brief asked appears in the "has not
 * posted" chase list and gets nagged. A false match credits a campaign with coverage it did not
 * earn, and that number goes to the client.
 */
class CaptionMatcherTest {

    private static final String CAMPAIGN_TAG = "klsummer24a7f3";

    @Nested
    @DisplayName("The campaign tracking tag")
    class CampaignTag {

        @Test
        void matchesExactly() {
            Optional<CaptionMatcher.Match> match = CaptionMatcher.matchCampaignTag(
                    "Loving this one ☀️ #klsummer24a7f3 #ad", CAMPAIGN_TAG);

            assertTrue(match.isPresent());
            assertTrue(match.get().campaignTag(), "a campaign tag match must be flagged as one");
            assertEquals("#klsummer24a7f3", match.get().foundAs());
        }

        @Test
        void ignoresCaseAndUnderscores() {
            // Creators type these on a phone. "#KL_Summer24A7F3" has done what the brief asked.
            assertTrue(CaptionMatcher.matchCampaignTag("#KLSummer24A7F3", CAMPAIGN_TAG).isPresent());
            assertTrue(CaptionMatcher.matchCampaignTag("#kl_summer24a7f3", CAMPAIGN_TAG).isPresent());
        }

        @Test
        void doesNotMatchOnASubstring() {
            // The failure that would credit a campaign with somebody else's post.
            assertTrue(CaptionMatcher.matchCampaignTag("#klsummer24a7f3extra", CAMPAIGN_TAG).isEmpty());
            assertTrue(CaptionMatcher.matchCampaignTag("#notklsummer24a7f3", CAMPAIGN_TAG).isEmpty());
        }

        @Test
        void findsItAnywhereInTheCaption() {
            // Hashtags usually sit in a block at the end, sometimes after line breaks.
            String caption = "New in.\n.\n.\n#gifted #beauty #klsummer24a7f3 #skincare";
            assertTrue(CaptionMatcher.matchCampaignTag(caption, CAMPAIGN_TAG).isPresent());
        }

        @Test
        void handlesAnEmptyOrAbsentCaption() {
            assertTrue(CaptionMatcher.matchCampaignTag(null, CAMPAIGN_TAG).isEmpty());
            assertTrue(CaptionMatcher.matchCampaignTag("", CAMPAIGN_TAG).isEmpty());
            assertTrue(CaptionMatcher.matchCampaignTag("#klsummer24a7f3", null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Brand hashtags and mentions")
    class BrandTerms {

        private static final String MONITORED = "#klgifting, #katieloxton";
        private static final String HANDLE = "katieloxton";

        @Test
        void matchesAMonitoredHashtag() {
            Optional<CaptionMatcher.Match> match = CaptionMatcher.matchBrandTerms(
                    "so pretty #katieloxton", MONITORED, HANDLE);

            assertTrue(match.isPresent());
            assertFalse(match.get().campaignTag(), "a brand tag is not a campaign tag");
        }

        @Test
        void matchesTheBrandMentionedByHandle() {
            // A creator who tags the brand rather than using a hashtag has still mentioned them.
            Optional<CaptionMatcher.Match> match = CaptionMatcher.matchBrandTerms(
                    "thank you @katieloxton for this!", MONITORED, HANDLE);

            assertTrue(match.isPresent());
            assertEquals("@katieloxton", match.get().foundAs());
        }

        @Test
        void handlesAMentionAtTheEndOfASentence() {
            // "@katieloxton." picks up the full stop, which would otherwise fail to match.
            assertTrue(CaptionMatcher.matchBrandTerms(
                    "Obsessed with @katieloxton.", MONITORED, HANDLE).isPresent());
        }

        @Test
        void toleratesHowTheSettingIsActuallyTyped() {
            // The monitored-hashtags field is free text and people fill it in inconsistently.
            for (String setting : new String[] {
                    "#klgifting,#katieloxton",
                    "klgifting katieloxton",
                    "  #KLGifting ;  KatieLoxton  "}) {
                assertTrue(CaptionMatcher.matchBrandTerms("look #klgifting", setting, HANDLE)
                        .isPresent(), "failed for setting: " + setting);
            }
        }

        @Test
        void doesNotMatchAnUnrelatedTag() {
            assertTrue(CaptionMatcher.matchBrandTerms(
                    "#somethingelse #beauty", MONITORED, HANDLE).isEmpty());
        }

        @Test
        void aShortTagDoesNotMatchALongerOne() {
            // The classic: #ad must not match #adidas.
            assertTrue(CaptionMatcher.matchBrandTerms("#adidas", "#ad", null).isEmpty());
        }

        @Test
        void copesWithNoBrandTermsConfigured() {
            assertTrue(CaptionMatcher.matchBrandTerms("#anything", null, null).isEmpty());
            assertTrue(CaptionMatcher.matchBrandTerms("#anything", "", "").isEmpty());
        }
    }

    @Nested
    @DisplayName("Extraction")
    class Extraction {

        @Test
        void pullsOutEveryHashtag() {
            assertEquals(java.util.Set.of("beauty", "gifted", "skincare"),
                    CaptionMatcher.hashtagsIn("#beauty love it #gifted\n#skincare"));
        }

        @Test
        void handlesAccentedAndNonLatinTags() {
            // Instagram accepts these and a UK agency working across Europe will see them.
            assertTrue(CaptionMatcher.hashtagsIn("#belleza #beauté").contains("beaute"),
                    "accents should be folded so #beauté and #beaute are one tag");
            assertTrue(CaptionMatcher.hashtagsIn("#日本").contains("日本"),
                    "non-Latin tags must survive rather than being stripped to nothing");
        }

        @Test
        void ignoresAHashInTheMiddleOfAWord() {
            assertTrue(CaptionMatcher.hashtagsIn("number1#notatag").contains("notatag"));
        }

        @Test
        void returnsNothingForAnEmptyCaption() {
            assertTrue(CaptionMatcher.hashtagsIn(null).isEmpty());
            assertTrue(CaptionMatcher.hashtagsIn("   ").isEmpty());
            assertTrue(CaptionMatcher.mentionsIn(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Disclosure")
    class Disclosure {

        @Test
        void recognisesTheUsualMarkers() {
            // Not attribution — a compliance signal. Under the CAP Code a gifted post has to be
            // disclosed, and a campaign where nobody has is worth noticing before the ASA does.
            for (String caption : new String[] {
                    "lovely #ad", "#gifted from them", "#PaidPartnership", "#SPONSORED"}) {
                assertTrue(CaptionMatcher.looksDisclosed(caption), caption);
            }
        }

        @Test
        void doesNotInventDisclosureThatIsNotThere() {
            assertFalse(CaptionMatcher.looksDisclosed("just a normal post #beauty"));
            assertFalse(CaptionMatcher.looksDisclosed(null));
            // "advertising" is not "#ad", and treating it as disclosure would be a false clean
            // bill of health on a compliance check.
            assertFalse(CaptionMatcher.looksDisclosed("#advertisingagency"));
        }
    }

    @Nested
    @DisplayName("Normalisation")
    class Normalisation {

        @Test
        void stripsTheLeadingSymbolCaseAndPunctuation() {
            assertEquals("klsummer", CaptionMatcher.normalise("#KL_Summer"));
            assertEquals("klsummer", CaptionMatcher.normalise("kl.summer"));
            assertEquals("katieloxton", CaptionMatcher.normalise("@KatieLoxton"));
        }

        @Test
        void aTagOfOnlyPunctuationBecomesNothing() {
            assertNull(CaptionMatcher.normalise("#___"));
            assertNull(CaptionMatcher.normalise("#"));
            assertNull(CaptionMatcher.normalise(null));
        }
    }
}
