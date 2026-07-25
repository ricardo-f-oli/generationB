CREATE TABLE outreach_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    subject_template TEXT NOT NULL,
    body_template TEXT NOT NULL,
    supported_tokens JSONB,
    ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outreach_templates_brand_id ON outreach_templates(brand_id);
