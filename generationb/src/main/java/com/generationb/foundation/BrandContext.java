package com.generationb.foundation;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import java.util.UUID;

@Component("brandContext")
@RequestScope
public class BrandContext {
    private static final ThreadLocal<UUID> currentBrandId = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    public UUID getBrandId() {
        return currentBrandId.get();
    }

    public void setBrandId(UUID brandId) {
        currentBrandId.set(brandId);
    }

    public UUID getUserId() {
        return currentUserId.get();
    }

    public void setUserId(UUID userId) {
        currentUserId.set(userId);
    }

    public static UUID getCurrentBrandId() {
        return currentBrandId.get();
    }

    public static void setCurrentBrandId(UUID brandId) {
        currentBrandId.set(brandId);
    }

    public static UUID getCurrentUserId() {
        return currentUserId.get();
    }

    public static void setCurrentUserId(UUID userId) {
        currentUserId.set(userId);
    }

    public static void clear() {
        currentBrandId.remove();
        currentUserId.remove();
    }
}
