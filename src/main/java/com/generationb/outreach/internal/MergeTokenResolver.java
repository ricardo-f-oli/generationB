package com.generationb.outreach.internal;

import com.generationb.shared.CreatorLookupPort;
import com.generationb.foundation.BrandLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves merge tokens in outreach subject/body copy.
 *
 * <p>Q-E3: the {@code {last_worked_with}} token used to be resolved by publishing an
 * ApplicationEvent and then immediately reading a shared map — correct only by accident of Spring
 * events being synchronous. It now calls a published port.
 *
 * <p>Q-J5: {@code {brand}} used to resolve to the brand's raw UUID, which was then emailed to
 * creators verbatim.
 */
@Service
public class MergeTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(MergeTokenResolver.class);

    private final CreatorLookupPort creatorLookup;
    private final BrandLookupPort brandLookup;

    public MergeTokenResolver(CreatorLookupPort creatorLookup, BrandLookupPort brandLookup) {
        this.creatorLookup = creatorLookup;
        this.brandLookup = brandLookup;
    }

    public String resolveText(String templateText, OutreachRecipient recipient,
                              OutreachCampaign campaign, String brandNameOverride) {
        if (templateText == null) {
            return "";
        }

        String result = templateText;
        result = replaceToken(result, "{first_name}", recipient.getCreatorFirstName());
        result = replaceToken(result, "{handle}", recipient.getCreatorHandle());
        result = replaceToken(result, "{brand}", resolveBrandName(recipient, brandNameOverride));
        result = replaceToken(result, "{product}", campaign != null ? campaign.getProductName() : null);

        if (result.contains("{last_worked_with}")) {
            String lastWorkedWith = creatorLookup
                    .findLastWorkedWith(recipient.getCreatorId(), recipient.getBrandId())
                    .orElse("");
            result = replaceToken(result, "{last_worked_with}", lastWorkedWith);
        }

        return result;
    }

    private String resolveBrandName(OutreachRecipient recipient, String override) {
        if (override != null && !override.isBlank() && !isUuid(override)) {
            return override;
        }
        UUID brandId = recipient.getBrandId();
        if (brandId == null) {
            return "";
        }
        return brandLookup.findBrandName(brandId).orElse("");
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String replaceToken(String input, String token, String value) {
        if (!input.contains(token)) {
            return input;
        }
        if (value == null || value.trim().isEmpty()) {
            log.warn("Merge token {} had no value; substituting an empty string", token);
            return input.replace(token, "");
        }
        return input.replace(token, value);
    }
}
