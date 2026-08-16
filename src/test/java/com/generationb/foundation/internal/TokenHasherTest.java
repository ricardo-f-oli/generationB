package com.generationb.foundation.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Q-B7: the digest must be deterministic so it can be indexed and looked up in one query. */
class TokenHasherTest {

    @Test
    void digestIsDeterministic() {
        String token = TokenHasher.generateToken();
        assertEquals(TokenHasher.digest(token), TokenHasher.digest(token));
    }

    @Test
    void differentTokensProduceDifferentDigests() {
        assertNotEquals(TokenHasher.digest(TokenHasher.generateToken()),
                        TokenHasher.digest(TokenHasher.generateToken()));
    }

    @Test
    void tokensAreUrlSafeAndHighEntropy() {
        String token = TokenHasher.generateToken();
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "token must be URL-safe: " + token);
        assertTrue(token.length() >= 40, "expected 256 bits of entropy, got " + token.length() + " chars");
    }

    @Test
    void digestIsHexSha256() {
        assertEquals(64, TokenHasher.digest("anything").length());
    }
}
