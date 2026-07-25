CREATE TABLE email_threads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outreach_recipient_id UUID NOT NULL REFERENCES outreach_recipients(id),
    brand_id UUID NOT NULL,
    direction VARCHAR(20) NOT NULL,
    sendgrid_message_id VARCHAR(255),
    from_address VARCHAR(255) NOT NULL,
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body_text TEXT,
    body_html TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_email_threads_recipient_id ON email_threads(outreach_recipient_id);
CREATE INDEX idx_email_threads_brand_id ON email_threads(brand_id);
