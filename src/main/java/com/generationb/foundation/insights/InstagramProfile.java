package com.generationb.foundation.insights;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A public Instagram profile and its recent posts, in our own shape.
 *
 * <p>Every {@link InstagramProfileSource} maps onto this, so nothing downstream — profile refresh,
 * auto-clipping, coverage, reports — knows or cares whether the data came from Meta's Graph API
 * or any source added later.
 */
public record InstagramProfile(
        String username,
        String fullName,
        String biography,
        long followers,
        List<Post> posts,
        /** Which source answered, e.g. {@code META}. */
        String source) {

    /**
     * One post.
     *
     * @param likes    null when the owner hid the like count — not zero
     * @param views    null where the source reports no view count (Business Discovery never does)
     * @param postType REEL, VIDEO, CAROUSEL or POST
     * @param url      canonical {@code https://www.instagram.com/p/{code}/}, so a post logged by hand
     *                 and later clipped automatically has the same URL and is not logged twice
     */
    public record Post(
            String id,
            String url,
            String caption,
            Long likes,
            long comments,
            Long views,
            String postType,
            Instant postedAt) {
    }

    private static final Pattern SHORTCODE =
            Pattern.compile("instagram\\.com/(?:[^/]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)");

    /**
     * Instagram links to the same post as {@code /p/}, {@code /reel/} or {@code /tv/}. Coverage
     * dedupes on URL, so they are normalised to one form.
     */
    public static String canonicalUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = SHORTCODE.matcher(url);
        return matcher.find() ? "https://www.instagram.com/p/" + matcher.group(1) + "/" : url;
    }
}
