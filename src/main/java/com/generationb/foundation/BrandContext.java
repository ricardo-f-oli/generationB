package com.generationb.foundation;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Holds the tenant (brand) and user for the current thread.
 *
 * <p>Previously this was annotated {@code @RequestScope} while storing everything in static
 * {@link ThreadLocal}s — the scope annotation did nothing, and any access from a scheduled job
 * threw {@code ScopeNotActiveException}. It is now a plain singleton over ThreadLocal storage
 * (Q-C4), which works identically inside and outside a web request.
 *
 * <p>It is still registered as a bean named {@code brandContext} because repository queries
 * reference {@code ?#{@brandContext.brandId}} in SpEL.
 */
@Component("brandContext")
public class BrandContext {

    private static final ThreadLocal<UUID> CURRENT_BRAND_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    /** Used by SpEL in {@code @Query} annotations. Never returns null inside a secured request. */
    public UUID getBrandId() {
        return CURRENT_BRAND_ID.get();
    }

    public UUID getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static UUID getCurrentBrandId() {
        return CURRENT_BRAND_ID.get();
    }

    public static UUID getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static String getCurrentRole() {
        return CURRENT_ROLE.get();
    }

    public static void set(UUID brandId, UUID userId, String role) {
        CURRENT_BRAND_ID.set(brandId);
        CURRENT_USER_ID.set(userId);
        CURRENT_ROLE.set(role);
    }

    /**
     * Brand of the caller, or a hard failure. Q-C5: writes used to silently fall back to a
     * hardcoded demo brand when the context was missing, which put other tenants' data in the
     * wrong place. Anything that needs a tenant now says so explicitly.
     */
    public static UUID requireBrandId() {
        UUID brandId = CURRENT_BRAND_ID.get();
        if (brandId == null) {
            throw new IllegalStateException("No brand context bound to the current request");
        }
        return brandId;
    }

    /** Runs an action bound to a specific tenant — used by scheduled jobs and public endpoints. */
    public static void runAs(UUID brandId, UUID userId, Runnable action) {
        UUID prevBrand = CURRENT_BRAND_ID.get();
        UUID prevUser = CURRENT_USER_ID.get();
        String prevRole = CURRENT_ROLE.get();
        try {
            set(brandId, userId, prevRole);
            action.run();
        } finally {
            CURRENT_BRAND_ID.set(prevBrand);
            CURRENT_USER_ID.set(prevUser);
            CURRENT_ROLE.set(prevRole);
        }
    }

    public static void clear() {
        CURRENT_BRAND_ID.remove();
        CURRENT_USER_ID.remove();
        CURRENT_ROLE.remove();
    }
}
