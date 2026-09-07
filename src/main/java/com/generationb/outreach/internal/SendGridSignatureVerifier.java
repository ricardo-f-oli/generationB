package com.generationb.outreach.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies that a SendGrid event webhook actually came from SendGrid.
 *
 * <p>This replaces a stub that returned {@code true} for every header except the literal string
 * {@code "INVALID_SIG"}. The endpoint is {@code permitAll} — it has to be, SendGrid cannot log in
 * — so anyone who knew the URL could post a payload that marked recipients as replied, flagged
 * creators for spam, or silently suppressed a creator the agency wanted to work with.
 *
 * <h2>The scheme</h2>
 * SendGrid signs with ECDSA over the NIST P-256 curve, SHA-256 digest. The signed payload is the
 * timestamp header concatenated with the <em>raw</em> request body, so the body has to be verified
 * exactly as it arrived — re-serialising a parsed {@code List<Map>} changes whitespace and key
 * order and the signature stops matching. The controller therefore takes the body as a String.
 *
 * <h2>Failing closed</h2>
 * No configured public key means every call is rejected. An unverified webhook is worse than a
 * missing one: it looks like it works while accepting anything. {@code allow-unsigned} exists for
 * local development and logs a warning on every single call so it cannot be left on by accident.
 *
 * @see <a href="https://www.twilio.com/docs/sendgrid/for-developers/tracking-events/getting-started-event-webhook-security-features">SendGrid event webhook security</a>
 */
@Slf4j
@Component
public class SendGridSignatureVerifier {

    /** The curve and digest SendGrid uses. Not configurable — it is their scheme, not ours. */
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";

    public enum Result {
        VALID,
        /** Signature did not verify: wrong key, tampered body, or a forgery. */
        INVALID,
        /** Verified, but old enough to be a replay of a message captured earlier. */
        STALE,
        /** Headers absent — not a signed SendGrid request at all. */
        MISSING,
        /** No public key configured, so nothing can be verified. */
        NOT_CONFIGURED
    }

    @Value("${outreach.sendgrid.webhook-public-key:}")
    private String publicKeyBase64;

    /**
     * How far out of step with SendGrid's clock a request may be. Ten minutes is SendGrid's own
     * suggestion: long enough to survive a retry and some clock drift, short enough that a
     * captured request is not replayable tomorrow.
     */
    @Value("${outreach.sendgrid.webhook-tolerance-seconds:600}")
    private long toleranceSeconds;

    /** Local development only. Every call it lets through is logged as a warning. */
    @Value("${outreach.sendgrid.webhook-allow-unsigned:false}")
    private boolean allowUnsigned;

    /** Parsed once: decoding the key on every event would be wasteful and can fail loudly here. */
    private PublicKey cachedKey;
    private boolean keyParseFailed;

    public boolean isConfigured() {
        return publicKeyBase64 != null && !publicKeyBase64.isBlank();
    }

    public boolean isAllowingUnsigned() {
        return allowUnsigned;
    }

    /**
     * @param signature the {@code X-Twilio-Email-Event-Webhook-Signature} header (base64 DER)
     * @param timestamp the {@code X-Twilio-Email-Event-Webhook-Timestamp} header (epoch seconds)
     * @param rawBody   the request body exactly as received, before any parsing
     */
    public Result verify(String signature, String timestamp, String rawBody) {
        if (!isConfigured()) {
            return Result.NOT_CONFIGURED;
        }
        if (isBlank(signature) || isBlank(timestamp) || rawBody == null) {
            return Result.MISSING;
        }

        PublicKey key = publicKey();
        if (key == null) {
            return Result.NOT_CONFIGURED;
        }

        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(key);
            // Timestamp first, then the untouched body. Signing the parsed body instead is the
            // classic way to get this wrong.
            verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));
            verifier.update(rawBody.getBytes(StandardCharsets.UTF_8));

            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                return Result.INVALID;
            }
            // Freshness is only meaningful once the signature holds: the timestamp is part of
            // what was signed, so an attacker cannot move it without breaking the signature.
            return isFresh(timestamp) ? Result.VALID : Result.STALE;

        } catch (IllegalArgumentException e) {
            // Signature header was not valid base64.
            return Result.INVALID;
        } catch (Exception e) {
            log.warn("SendGrid signature verification failed: {}", e.getClass().getSimpleName());
            return Result.INVALID;
        }
    }

    private boolean isFresh(String timestamp) {
        try {
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            Duration drift = Duration.between(sent, Instant.now()).abs();
            return drift.getSeconds() <= toleranceSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * SendGrid hands out the key as base64 DER {@code SubjectPublicKeyInfo}, which is what
     * {@link X509EncodedKeySpec} expects. A bad key is logged once rather than on every event.
     */
    private synchronized PublicKey publicKey() {
        if (cachedKey != null || keyParseFailed) {
            return cachedKey;
        }
        try {
            byte[] der = Base64.getDecoder().decode(publicKeyBase64.trim());
            cachedKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(der));
            log.info("SendGrid webhook signature verification is active");
        } catch (Exception e) {
            keyParseFailed = true;
            log.error("SENDGRID_WEBHOOK_PUBLIC_KEY is not a valid base64 EC public key ({}). "
                    + "Every webhook call will be rejected until it is corrected.",
                    e.getClass().getSimpleName());
        }
        return cachedKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
