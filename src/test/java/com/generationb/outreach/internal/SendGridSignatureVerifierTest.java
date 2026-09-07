package com.generationb.outreach.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The webhook endpoint is unauthenticated by necessity — SendGrid cannot log in — so this class
 * is the only thing standing between a stranger and the ability to mark creators as having
 * replied, or file a spam report that suppresses them across every brand.
 *
 * <p>The signature it replaced was {@code !"INVALID_SIG".equals(signature)}, so these tests are
 * written to fail if anything like that comes back: a real key pair signs a real payload, and
 * every rejection case is asserted rather than assumed.
 */
class SendGridSignatureVerifierTest {

    private static final String BODY =
            "[{\"event\":\"open\",\"sg_message_id\":\"abc.123\"}]";

    private KeyPair keyPair;
    private SendGridSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        // SendGrid signs on NIST P-256. Generating a pair here means the test exercises the real
        // algorithm rather than a stand-in for it.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();

        verifier = new SendGridSignatureVerifier();
        configure(verifier, keyPair);
    }

    private static void configure(SendGridSignatureVerifier target, KeyPair pair) {
        ReflectionTestUtils.setField(target, "publicKeyBase64",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(target, "toleranceSeconds", 600L);
        ReflectionTestUtils.setField(target, "allowUnsigned", false);
    }

    /** Signs exactly the way SendGrid does: timestamp first, then the raw body. */
    private String sign(String timestamp, String body) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(timestamp.getBytes(StandardCharsets.UTF_8));
        signature.update(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    // =====================================================================

    @Test
    void aGenuinelySignedRequestIsAccepted() throws Exception {
        String timestamp = now();
        assertEquals(SendGridSignatureVerifier.Result.VALID,
                verifier.verify(sign(timestamp, BODY), timestamp, BODY));
    }

    @Test
    void aTamperedBodyIsRejected() throws Exception {
        String timestamp = now();
        String signature = sign(timestamp, BODY);

        // The attack this exists to stop: intercept a real event, change whose recipient it is,
        // replay it. The signature covers the body, so it no longer verifies.
        String tampered = BODY.replace("\"open\"", "\"spamreport\"");
        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                verifier.verify(signature, timestamp, tampered));
    }

    @Test
    void aSignatureFromAnotherKeyIsRejected() throws Exception {
        String timestamp = now();
        String signature = sign(timestamp, BODY);

        // Correctly formed, correctly signed — by somebody else.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        SendGridSignatureVerifier other = new SendGridSignatureVerifier();
        configure(other, generator.generateKeyPair());

        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                other.verify(signature, timestamp, BODY));
    }

    @Test
    void anOldButOtherwiseValidRequestIsRejectedAsAReplay() throws Exception {
        // Signed properly an hour ago. Without the freshness check, anyone who captured a
        // delivered webhook could replay it indefinitely.
        String timestamp = String.valueOf(Instant.now().minusSeconds(3_600).getEpochSecond());
        assertEquals(SendGridSignatureVerifier.Result.STALE,
                verifier.verify(sign(timestamp, BODY), timestamp, BODY));
    }

    @Test
    void aTimestampMovedForwardToDefeatTheReplayCheckIsRejected() throws Exception {
        // The timestamp is itself signed, so editing it to look fresh breaks the signature.
        String old = String.valueOf(Instant.now().minusSeconds(3_600).getEpochSecond());
        String signature = sign(old, BODY);

        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                verifier.verify(signature, now(), BODY));
    }

    @Test
    void missingHeadersAreReportedAsMissingRatherThanInvalid() {
        // The caller distinguishes these: MISSING can be waved through in local development,
        // INVALID never can.
        assertEquals(SendGridSignatureVerifier.Result.MISSING,
                verifier.verify(null, now(), BODY));
        assertEquals(SendGridSignatureVerifier.Result.MISSING,
                verifier.verify("someSignature", null, BODY));
        assertEquals(SendGridSignatureVerifier.Result.MISSING,
                verifier.verify("   ", now(), BODY));
    }

    @Test
    void garbageInTheSignatureHeaderIsRejectedRatherThanThrowing() {
        // Not base64. Must be a clean rejection, not a 500 that tells the caller they found an
        // unhandled path.
        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                verifier.verify("!!!not base64!!!", now(), BODY));
    }

    @Test
    void theOldStubBehaviourIsGone() {
        // The previous implementation accepted every header except this exact string. Both of
        // these must now be rejected.
        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                verifier.verify("INVALID_SIG", now(), BODY));
        assertEquals(SendGridSignatureVerifier.Result.INVALID,
                verifier.verify("anything-at-all", now(), BODY));
    }

    @Test
    void withNoKeyConfiguredNothingIsVerifiable() throws Exception {
        SendGridSignatureVerifier unconfigured = new SendGridSignatureVerifier();
        ReflectionTestUtils.setField(unconfigured, "publicKeyBase64", "");
        ReflectionTestUtils.setField(unconfigured, "toleranceSeconds", 600L);
        ReflectionTestUtils.setField(unconfigured, "allowUnsigned", false);

        String timestamp = now();
        assertFalse(unconfigured.isConfigured());
        // NOT_CONFIGURED rather than VALID: the controller fails closed on this.
        assertEquals(SendGridSignatureVerifier.Result.NOT_CONFIGURED,
                unconfigured.verify(sign(timestamp, BODY), timestamp, BODY));
    }

    @Test
    void aMalformedPublicKeyRejectsEverythingRatherThanAcceptingIt() throws Exception {
        SendGridSignatureVerifier broken = new SendGridSignatureVerifier();
        ReflectionTestUtils.setField(broken, "publicKeyBase64", "bm90LWEta2V5");
        ReflectionTestUtils.setField(broken, "toleranceSeconds", 600L);
        ReflectionTestUtils.setField(broken, "allowUnsigned", false);

        String timestamp = now();
        assertEquals(SendGridSignatureVerifier.Result.NOT_CONFIGURED,
                broken.verify(sign(timestamp, BODY), timestamp, BODY));
    }
}
