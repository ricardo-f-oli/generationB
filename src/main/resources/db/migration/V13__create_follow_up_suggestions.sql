CREATE TABLE follow_up_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outreach_recipient_id UUID NOT NULL REFERENCES outreach_recipients(id),
    template_id UUID NOT NULL REFERENCES outreach_templates(id),
    brand_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_follow_up_suggestions_recipient_id ON follow_up_suggestions(outreach_recipient_id);
