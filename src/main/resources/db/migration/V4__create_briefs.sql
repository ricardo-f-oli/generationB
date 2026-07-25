CREATE TABLE briefs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    campaign_name VARCHAR(255) NOT NULL,
    campaign_goal TEXT,
    key_messages TEXT,
    deliverables JSONB,
    budget_min NUMERIC(19, 2),
    budget_max NUMERIC(19, 2),
    timeline_start TIMESTAMP WITH TIME ZONE,
    timeline_end TIMESTAMP WITH TIME ZONE,
    tone_of_voice VARCHAR(50),
    additional_notes TEXT,
    ai_generated_content TEXT,
    status VARCHAR(50) NOT NULL,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE contract_clauses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    clause_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    display_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);
