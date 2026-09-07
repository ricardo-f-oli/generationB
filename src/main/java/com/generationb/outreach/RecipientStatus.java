package com.generationb.outreach;

/**
 * Requirement #31: Sent, Opened, Replied, Declined, No Response.
 *
 * <p>Q-J10: DECLINED and NO_RESPONSE were previously unreachable. NO_RESPONSE is now applied by
 * the follow-up scanner once the no-reply window closes; DECLINED is set from the reply-handling
 * path. FAILED is new — Q-E12: a recipient whose send actually failed used to be recorded as SENT.
 *
 * <p>DELIVERED and UNSUBSCRIBED arrive from the SendGrid event webhook. Neither could be reached
 * before, because the webhook accepted forged calls and was therefore not trusted to write state.
 * The lifecycle now runs NOT_SENT to SENT to DELIVERED to OPENED to REPLIED, with FAILED, BOUNCED
 * and UNSUBSCRIBED as terminal outcomes.
 */
public enum RecipientStatus {
    NOT_SENT,
    /** Handed to SendGrid without error. Not the same as arriving. */
    SENT,
    /** SendGrid accepted it and the receiving server took it. */
    DELIVERED,
    FAILED,
    OPENED,
    REPLIED,
    DECLINED,
    NO_RESPONSE,
    BOUNCED,
    /**
     * The creator used the unsubscribe link. Set from the event webhook, which also raises a
     * {@code CreatorFlaggedEvent} so the global suppression list picks them up (#21).
     */
    UNSUBSCRIBED
}
