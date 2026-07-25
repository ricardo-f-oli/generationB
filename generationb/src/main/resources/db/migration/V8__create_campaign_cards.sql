CREATE TABLE campaign_cards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    board_id UUID NOT NULL REFERENCES kanban_boards(id),
    column_id UUID NOT NULL REFERENCES kanban_columns(id),
    brand_id UUID NOT NULL REFERENCES brands(id),
    creator_id UUID NOT NULL,
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    deliverables JSONB,
    fee_amount NUMERIC(19, 2),
    fee_currency VARCHAR(3),
    deadline DATE,
    payment_status VARCHAR(50) NOT NULL,
    content_draft_urls JSONB,
    approval_status VARCHAR(50) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);
