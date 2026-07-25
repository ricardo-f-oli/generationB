CREATE TABLE outreach_recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outreach_campaign_id UUID NOT NULL REFERENCES outreach_campaigns(id),
    brand_id UUID NOT NULL,
    creator_id UUID NOT NULL,
    creator_email VARCHAR(255),
    creator_first_name VARCHAR(255),
    creator_handle VARCHAR(255),
    resolved_subject VARCHAR(255),
    resolved_body TEXT,
    sendgrid_message_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    opened_at TIMESTAMP WITH TIME ZONE,
    replied_at TIMESTAMP WITH TIME ZONE,
    follow_up_suggested_at TIMESTAMP WITH TIME ZONE,
    follow_up_sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outreach_recipients_campaign_id ON outreach_recipients(outreach_campaign_id);
CREATE INDEX idx_outreach_recipients_brand_id ON outreach_recipients(brand_id);
CREATE INDEX idx_outreach_recipients_sendgrid_id ON outreach_recipients(sendgrid_message_id);
