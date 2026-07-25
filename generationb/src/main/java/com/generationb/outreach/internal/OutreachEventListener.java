package com.generationb.outreach.internal;

import com.generationb.shared.CardMovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for cross-module application events from campaigns and other modules.
 */
@Component
public class OutreachEventListener {

    private static final Logger log = LoggerFactory.getLogger(OutreachEventListener.class);

    @EventListener
    public void handleCardMoved(CardMovedEvent event) {
        log.info("Received CardMovedEvent for card {} from column {} to column {}",
            event.cardId(), event.fromColumnId(), event.toColumnId());
        // Handle trigger_email check on target column if configured
    }
}
