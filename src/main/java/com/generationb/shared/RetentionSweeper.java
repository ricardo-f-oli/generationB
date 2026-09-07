package com.generationb.shared;

import java.time.Instant;

/**
 * Requirement #37: one module's answer to "how long do we keep this, and why".
 *
 * <p>GDPR's storage limitation principle needs data removed once the purpose it was collected for
 * has passed. That could have been a single class issuing deletes against every table, but the
 * cutoff for a home address is not the cutoff for a login attempt, and the reasoning belongs next
 * to the data rather than in a list somewhere else. Each module implements this for what it owns;
 * {@code DataRetentionService} finds every implementation and runs it.
 *
 * <p>Adding a table with personal data in it means adding a sweeper. That is the point: the
 * compiler will not remind you, but a reviewer looking at this interface will.
 */
public interface RetentionSweeper {

    /**
     * @param dataset  what is being swept, in the words the GDPR screen shows: "Gifting addresses"
     * @param policy   the rule in one sentence, including the window and the reason for it
     * @param affected how many rows this pass changed, or would have changed on a dry run
     * @param oldestRemaining age of the oldest record still held, for spotting a stuck sweeper
     */
    record Outcome(String dataset, String policy, int affected, Instant oldestRemaining) {

        public static Outcome of(String dataset, String policy, int affected) {
            return new Outcome(dataset, policy, affected, null);
        }
    }

    /** Shown on the GDPR screen and in the run log. Keep it a plain noun phrase. */
    String dataset();

    /**
     * The rule, in a sentence a non-engineer can check against the agency's privacy notice.
     * This is the text that appears on screen, so it is the thing that has to be true.
     */
    String policy();

    /**
     * Applies the policy.
     *
     * @param dryRun when true, count what would go but change nothing. The nightly job runs for
     *               real; the screen's "preview" runs dry, because nobody should have to trigger
     *               a deletion to find out how big it is.
     */
    Outcome sweep(boolean dryRun);
}
