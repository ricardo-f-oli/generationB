package com.generationb.outreach.internal;

import com.generationb.shared.ResolveLastWorkedWithQuery;
import com.generationb.shared.ResolveLastWorkedWithResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MergeTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(MergeTokenResolver.class);

    private final ApplicationEventPublisher eventPublisher;
    private final Map<UUID, String> pendingLastWorkedWithResponses = new ConcurrentHashMap<>();

    public MergeTokenResolver(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handleLastWorkedWithResponse(ResolveLastWorkedWithResponseEvent event) {
        if (event.lastWorkedWith() != null) {
            pendingLastWorkedWithResponses.put(event.requestId(), event.lastWorkedWith());
        } else {
            pendingLastWorkedWithResponses.put(event.requestId(), "");
        }
    }

    public String resolveText(String templateText, OutreachRecipient recipient, OutreachCampaign campaign, String brandName) {
        if (templateText == null) {
            return "";
        }

        String result = templateText;

        // {first_name} -> recipient.creator_first_name
        String firstName = recipient.getCreatorFirstName();
        result = replaceToken(result, "{first_name}", firstName);

        // {handle} -> recipient.creator_handle
        String handle = recipient.getCreatorHandle();
        result = replaceToken(result, "{handle}", handle);

        // {brand} -> brandName or passed campaign brand
        result = replaceToken(result, "{brand}", brandName);

        // {product} -> campaign.productName
        String product = campaign != null ? campaign.getProductName() : null;
        result = replaceToken(result, "{product}", product);

        // {last_worked_with} -> Resolve via application event
        if (result.contains("{last_worked_with}")) {
            String lastWorkedWith = resolveLastWorkedWith(recipient.getCreatorId(), recipient.getBrandId());
            result = replaceToken(result, "{last_worked_with}", lastWorkedWith);
        }

        return result;
    }

    private String resolveLastWorkedWith(UUID creatorId, UUID brandId) {
        if (creatorId == null) {
            return "";
        }
        UUID requestId = UUID.randomUUID();
        eventPublisher.publishEvent(new ResolveLastWorkedWithQuery(requestId, creatorId, brandId));
        String response = pendingLastWorkedWithResponses.remove(requestId);
        return response != null ? response : "";
    }

    private String replaceToken(String input, String token, String value) {
        if (!input.contains(token)) {
            return input;
        }

        if (value == null || value.trim().isEmpty()) {
            log.warn("Token {} missing data, replacing with empty string.", token);
            return input.replace(token, "");
        }

        return input.replace(token, value);
    }
}
