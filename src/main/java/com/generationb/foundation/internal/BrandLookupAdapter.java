package com.generationb.foundation.internal;

import com.generationb.foundation.Brand;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandLookupAdapter implements BrandLookupPort {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "brandProfiles", unless = "#result == null")
    public Optional<BrandProfile> findProfile(UUID brandId) {
        if (brandId == null) {
            return Optional.empty();
        }
        return brandRepository.findById(brandId).map(this::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findBrandName(UUID brandId) {
        return findProfile(brandId).map(BrandProfile::name);
    }

    private BrandProfile toProfile(Brand brand) {
        return new BrandProfile(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getLogoUrl(),
                brand.getPrimaryColour(),
                brand.getToneOfVoice(),
                brand.getBrandGuidelines(),
                brand.getInstagramHandle(),
                brand.getMonitoredHashtags(),
                brand.getReplyToEmail(),
                brand.getFromName()
        );
    }
}
