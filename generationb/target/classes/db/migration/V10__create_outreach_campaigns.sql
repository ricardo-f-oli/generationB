CREATE TABLE outreach_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL,
    campaign_id UUID,
    template_id UUID REFERENCES outreach_templates(id),
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    product_name VARCHAR(255),
    outreach_type VARCHAR(50) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,
    created_by UUID,
    no_reply_window_days INT NOT NULL DEFAULT 7,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outreach_campaigns_brand_id ON outreach_campaigns(brand_id);
CREATE INDEX idx_outreach_campaigns_status ON outreach_campaigns(status);
