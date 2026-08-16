package com.generationb.foundation.internal;

import com.generationb.foundation.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Q-B9: 12 characters minimum, no composition rules, common passwords rejected. */
class PasswordPolicyTest {

    @Test
    void acceptsAMemorablePassphrase() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("correct horse battery staple"));
    }

    @Test
    void rejectsAnythingShorterThanTwelveCharacters() {
        ApiException ex = assertThrows(ApiException.class, () -> PasswordPolicy.validate("Short1!"));
        assertTrue(ex.getMessage().contains("12"));
    }

    @Test
    void rejectsWellKnownPasswords() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("password123"));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("qwerty123456"));
    }

    @Test
    void rejectsRepeatedCharacters() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("aaaaaaaaaaaaaa"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validate(null));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("   "));
    }
}
