package com.generationb.outreach;

/**
 * Requirement #31: Sent, Opened, Replied, Declined, No Response.
 *
 * <p>Q-J10: DECLINED and NO_RESPONSE were previously unreachable. NO_RESPONSE is now applied by
 * the follow-up scanner once the no-reply window closes; DECLINED is set from the reply-handling
 * path. FAILED is new — Q-E12: a recipient whose send actually failed used to be recorded as SENT.
 */
public enum RecipientStatus {
    NOT_SENT,
    SENT,
    FAILED,
    OPENED,
    REPLIED,
    DECLINED,
    NO_RESPONSE,
    BOUNCED,
    UNSUBSCRIBED
}
