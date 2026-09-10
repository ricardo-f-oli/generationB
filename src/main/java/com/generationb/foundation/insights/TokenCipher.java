package com.generationb.foundation.insights;

import com.generationb.foundation.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the OAuth tokens creators hand us when they connect an account.
 *
 * <h2>Why encryption rather than hashing</h2>
 *
 * <p>Everywhere else in this codebase a token is hashed — password resets, refresh tokens — because
 * we only ever need to check whether a presented value matches. These are different: they are
 * live credentials for <em>somebody else's</em> Instagram or YouTube account, and we have to be
 * able to send them to Meta and Google. A hash cannot be sent. So they are encrypted, and the
 * threat being defended against is narrower but real: a leaked database dump, a careless backup,
 * a support query that reads a row.
 *
 * <p>AES-256-GCM. Authenticated, so a tampered ciphertext fails to decrypt rather than producing
 * plausible garbage, and a fresh random nonce per encryption so the same token never produces the
 * same ciphertext twice.
 *
 * <h2>Failing closed</h2>
 *
 * <p>No key means connections cannot be stored at all. The alternative — falling back to plain
 * text so the feature keeps working — would put third-party credentials in a database column
 * while everything looked fine, which is precisely the sort of quiet compromise nobody discovers
 * until it matters.
 */
@Slf4j
@Component
public class TokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;   // 96 bits, the size GCM is specified for
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    /**
     * Base64 of 32 random bytes. Generate with:
     * {@code openssl rand -base64 32}
     */
    @Value("${insights.token-encryption-key:}")
    private String encodedKey;

    private SecretKey key;

    @PostConstruct
    void loadKey() {
        if (encodedKey == null || encodedKey.isBlank()) {
            log.warn("INSIGHTS_TOKEN_ENCRYPTION_KEY is not set. Creators will not be able to "
                    + "connect their accounts — audience demographics stay unavailable until a "
                    + "key is configured. Generate one with: openssl rand -base64 32");
            return;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encodedKey.trim());
            if (raw.length != 32) {
                // A short key is worse than no key: it looks configured and is weak.
                log.error("INSIGHTS_TOKEN_ENCRYPTION_KEY decodes to {} bytes; AES-256 needs 32. "
                        + "Account connections are disabled.", raw.length);
                return;
            }
            key = new SecretKeySpec(raw, "AES");
            log.info("Creator token encryption is active");
        } catch (IllegalArgumentException e) {
            log.error("INSIGHTS_TOKEN_ENCRYPTION_KEY is not valid base64. "
                    + "Account connections are disabled.");
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** @return nonce and ciphertext, base64, as one string for a single column */
    public String encrypt(String plaintext) {
        requireKey();
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Nonce prefixed rather than stored separately: it is not secret, it only has to be
            // unique, and one column is one fewer thing to get out of step.
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length)
                            .put(nonce).put(ciphertext).array());

        } catch (Exception e) {
            // Never log the exception itself — the message can carry the plaintext.
            log.error("Token encryption failed: {}", e.getClass().getSimpleName());
            throw ApiException.unprocessable("Could not securely store the connection.");
        }
    }

    public String decrypt(String stored) {
        requireKey();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("too short to contain a nonce");
            }
            ByteBuffer buffer = ByteBuffer.wrap(all);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            // Usually means the key was rotated without re-encrypting. Say so, because the
            // symptom otherwise is "connections silently stopped working".
            log.error("Token decryption failed ({}). If the encryption key was changed, existing "
                    + "connections cannot be read and creators must reconnect.",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private void requireKey() {
        if (key == null) {
            throw ApiException.unprocessable(
                    "Account connections are not available: no token encryption key is "
                            + "configured on the server.");
        }
    }
}
