package com.generationb.foundation.insights;

import java.util.Optional;

/**
 * Somewhere a public Instagram profile can be read from by handle, without the creator's
 * permission.
 *
 * <p>Implemented by {@link MetaInstagramProfileSource} (Business Discovery: official, free, needs a
 * Meta app). Chosen by {@link InstagramProfiles}.
 */
public interface InstagramProfileSource {

    /** A short source name, e.g. {@code META}. */
    String name();

    boolean isEnabled();

    /**
     * @param handle a validated handle, without {@code @}
     * @param posts  how many recent posts to include
     * @return empty when the account does not exist, is private, is not visible to this source,
     *         or the source failed. Callers report "no data" rather than guessing which.
     */
    Optional<InstagramProfile> fetch(String handle, int posts);
}
