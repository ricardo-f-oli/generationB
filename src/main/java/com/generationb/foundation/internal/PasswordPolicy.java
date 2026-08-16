package com.generationb.foundation.internal;

import com.generationb.foundation.ApiException;

import java.util.Set;

/**
 * Q-B9: the only rule used to be "at least 6 characters".
 *
 * <p>Per the answered brief: minimum 12 characters, no composition rules (no forced symbols or
 * mixed case — those push people towards predictable substitutions), plus a rejection list for
 * the passwords that actually get guessed first.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 200;

    /** Small embedded list: a real breach-corpus check needs a dependency we are not adding yet. */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "letmein",
            "qwertyuiop", "123456789012", "1234567890", "iloveyou",
            "administrator", "generationb", "welcome123", "changeme123",
            "abcdefghijkl", "qwerty123456"
    );

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Password is required");
        }
        if (password.length() < MIN_LENGTH) {
            throw ApiException.badRequest(
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("Password must be at most " + MAX_LENGTH + " characters");
        }
        String normalised = password.toLowerCase().replaceAll("\\s+", "");
        if (COMMON.contains(normalised)) {
            throw ApiException.badRequest("That password is too easy to guess. Please choose another.");
        }
        if (normalised.chars().distinct().count() < 4) {
            throw ApiException.badRequest("Password must not be a single repeated character");
        }
    }
}
