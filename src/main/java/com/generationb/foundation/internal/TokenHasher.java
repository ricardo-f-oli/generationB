package com.generationb.foundation.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Q-B7 / Q-B8: refresh and password-reset tokens were stored as BCrypt hashes, which forced a
 * full table scan plus one ~100ms BCrypt comparison per row on every refresh — an easy DoS.
 *
 * <p>These tokens are already 256 bits of cryptographic randomness, so they do not need a slow
 * password hash to resist brute force. A SHA-256 digest is deterministic, which means it can be
 * indexed and looked up in one query.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenHasher() {
    }

    /** 256 bits of entropy, URL-safe so it can be embedded in an unsubscribe link. */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String digest(String rawToken) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
