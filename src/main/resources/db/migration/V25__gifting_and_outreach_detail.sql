-- Gifting: the data EC's upload schema needs (requirement #42) and the reminder sequence
-- state (requirement #46), neither of which was modelled.

ALTER TABLE dispatches
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS packaging_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS planned_dispatch_date DATE,
    ADD COLUMN IF NOT EXISTS content_deadline DATE,
    ADD COLUMN IF NOT EXISTS reminder_week_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reminder_48h_sent_at TIMESTAMP WITH TIME ZONE;

UPDATE dispatches SET product_name = 'Mediheal Hydrating Box' WHERE product_name IS NULL;

ALTER TABLE dispatches ADD CONSTRAINT chk_dispatch_status
    CHECK (status IN ('READY_TO_DISPATCH', 'DISPATCHED', 'DELIVERED', 'RETURNED', 'DECLINED'));

-- Requirement #47: a refusal or return must flag the creator for future exclusion.
ALTER TABLE creators
    ADD COLUMN IF NOT EXISTS gifting_excluded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS gifting_exclusion_reason VARCHAR(255);

-- Requirement #43: capture the brand's confirmation of a direct-from-brand order.
CREATE TABLE brand_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
    brand_contact_email VARCHAR(255),
    recipient_count INT NOT NULL DEFAULT 0,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    confirm_token VARCHAR(128),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_brand_order_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REJECTED'))
);
CREATE INDEX idx_brand_orders_brand ON brand_orders(brand_id);

-- Q-I5: gifting consent must be captured, not defaulted to true.
ALTER TABLE gifting_addresses
    ALTER COLUMN gdpr_consent_flag SET DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS consent_source VARCHAR(50) NOT NULL DEFAULT 'IMPORTED',
    ADD COLUMN IF NOT EXISTS capture_token VARCHAR(128);
CREATE INDEX idx_gifting_addresses_creator ON gifting_addresses(creator_id);
CREATE INDEX idx_gifting_addresses_token ON gifting_addresses(capture_token);

-- Q-J9: unsubscribes and bounces from the provider must reach the suppression list, so the
-- recipient row needs somewhere to record them.
ALTER TABLE outreach_recipients
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS bounced_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS unsubscribed_at TIMESTAMP WITH TIME ZONE;

-- Q-G4: a follow-up suggestion stores its own draft instead of creating a template row per
-- recipient per day, which polluted the template library.
ALTER TABLE follow_up_suggestions
    ADD COLUMN IF NOT EXISTS draft_subject VARCHAR(255),
    ADD COLUMN IF NOT EXISTS draft_body TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'SUGGESTED';
ALTER TABLE follow_up_suggestions ALTER COLUMN template_id DROP NOT NULL;
