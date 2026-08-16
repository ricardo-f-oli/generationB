package com.generationb.foundation;

/**
 * Q-C11 / Q-C10: roles were free-text strings turned into authorities by concatenation, so an
 * absent claim produced {@code ROLE_null} and nothing rejected an unknown value. VIEW_ONLY was
 * referenced in {@code @PreAuthorize} but no user ever had it — it is dropped here.
 */
public enum Role {

    ADMIN,
    DIRECTOR,
    ACCOUNT_MANAGER,
    ACCOUNT_EXECUTIVE;

    public String authority() {
        return "ROLE_" + name();
    }

    /** @return the matching role, or {@code null} when the value is not recognised. */
    public static Role fromString(String value) {
        if (value == null) {
            return null;
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        return null;
    }
}
