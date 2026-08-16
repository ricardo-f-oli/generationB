-- Campaign workflow: configurable stages, card ordering, comments, saved views.
-- Q-E19: the frontend's 7-stage set is the correct one.
-- Q-E20: cards need deterministic ordering for drag-and-drop.

ALTER TABLE campaign_cards
    ADD COLUMN IF NOT EXISTS position INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS brief_id UUID REFERENCES briefs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_campaign_cards_board ON campaign_cards(board_id);
CREATE INDEX idx_campaign_cards_column ON campaign_cards(column_id);
CREATE INDEX idx_campaign_cards_brand ON campaign_cards(brand_id);
CREATE INDEX idx_campaign_cards_creator ON campaign_cards(creator_id);
CREATE INDEX idx_campaign_cards_assignee ON campaign_cards(assignee_id);

-- Q-5: comments were displayed in the UI but had no storage.
CREATE TABLE card_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES campaign_cards(id) ON DELETE CASCADE,
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    author_name VARCHAR(255),
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_card_comments_card ON card_comments(card_id);

-- Q-4 / Q-7: stages are defined per brand and instantiated onto each board.
CREATE TABLE board_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    campaign_type VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE board_template_columns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES board_templates(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    display_order INT NOT NULL,
    requires_director_approval BOOLEAN NOT NULL DEFAULT FALSE,
    requires_client_approval BOOLEAN NOT NULL DEFAULT FALSE,
    triggers_email BOOLEAN NOT NULL DEFAULT FALSE,
    trigger_template_id UUID
);
CREATE INDEX idx_board_template_columns_template ON board_template_columns(template_id);
CREATE INDEX idx_kanban_columns_board ON kanban_columns(board_id);
CREATE INDEX idx_kanban_boards_campaign ON kanban_boards(campaign_id);

-- Q-9: per-user saved views (my cards / blocked / awaiting approval / due this week).
CREATE TABLE saved_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    scope VARCHAR(30) NOT NULL DEFAULT 'BOARD',
    filter_json JSONB NOT NULL,
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_saved_views_user ON saved_views(user_id);

-- Seed the default 7-stage templates for the demo brand (Q-E19).
INSERT INTO board_templates (id, brand_id, name, campaign_type, is_default) VALUES
    ('b7111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Seeding Campaign', 'SEEDING', true),
    ('b7222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Paid Partnership', 'PAID', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO board_template_columns (template_id, name, display_order, requires_director_approval, requires_client_approval, triggers_email) VALUES
    ('b7111111-1111-1111-1111-111111111111', 'Target List',    1, false, false, false),
    ('b7111111-1111-1111-1111-111111111111', 'Brief Sent',     2, false, false, true),
    ('b7111111-1111-1111-1111-111111111111', 'Content Draft',  3, false, false, false),
    ('b7111111-1111-1111-1111-111111111111', 'Brand Review',   4, false, true,  false),
    ('b7111111-1111-1111-1111-111111111111', 'Approved',       5, true,  false, false),
    ('b7111111-1111-1111-1111-111111111111', 'Live',           6, false, false, false),
    ('b7111111-1111-1111-1111-111111111111', 'Reporting',      7, false, false, false),
    ('b7222222-2222-2222-2222-222222222222', 'Target List',    1, false, false, false),
    ('b7222222-2222-2222-2222-222222222222', 'Brief Sent',     2, false, false, true),
    ('b7222222-2222-2222-2222-222222222222', 'Contract Sent',  3, true,  false, false),
    ('b7222222-2222-2222-2222-222222222222', 'Content Draft',  4, false, false, false),
    ('b7222222-2222-2222-2222-222222222222', 'Brand Review',   5, false, true,  false),
    ('b7222222-2222-2222-2222-222222222222', 'Live',           6, false, false, false),
    ('b7222222-2222-2222-2222-222222222222', 'Reporting',      7, false, false, false);

-- Q-E23: clauses attach to briefs, and there was no clause data at all.
CREATE TABLE brief_clauses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brief_id UUID NOT NULL REFERENCES briefs(id) ON DELETE CASCADE,
    clause_id UUID NOT NULL REFERENCES contract_clauses(id) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_brief_clause UNIQUE (brief_id, clause_id)
);
CREATE INDEX idx_brief_clauses_brief ON brief_clauses(brief_id);

INSERT INTO contract_clauses (id, brand_id, clause_type, content, display_order, is_active) VALUES
    ('cc111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'NON_COMPETE', 'The Creator agrees not to promote a directly competing brand within the same product category for 30 days either side of the campaign live date.', 1, true),
    ('cc222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PAYMENT_TERMS', 'Payment is made within 30 days of receipt of a valid invoice, following approval of all contracted deliverables.', 2, true),
    ('cc333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'DELIVERABLE_WINDOW', 'All contracted deliverables must be published within the agreed campaign window. Content remains live for a minimum of 12 months.', 3, true),
    ('cc444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'SALE_PERIOD', 'The Creator agrees not to reference discounting or sale pricing during the exclusivity window unless expressly approved in writing.', 4, true),
    ('cc555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'LEGAL_DISCLAIMER', 'All content must carry clear advertising disclosure in line with ASA and CAP Code guidance (#ad or Paid Partnership label).', 5, true)
ON CONFLICT (id) DO NOTHING;

CREATE INDEX idx_briefs_brand ON briefs(brand_id);
CREATE INDEX idx_campaigns_brand ON campaigns(brand_id);
CREATE INDEX idx_contract_clauses_brand ON contract_clauses(brand_id);
CREATE INDEX idx_brief_shares_brief ON brief_shares(brief_id);
CREATE INDEX idx_coverage_items_brand ON coverage_items(brand_id);
CREATE INDEX idx_coverage_items_creator ON coverage_items(creator_id);
CREATE INDEX idx_dispatches_creator ON dispatches(creator_id);
CREATE INDEX idx_dispatches_run ON dispatches(gifting_run_id);
CREATE INDEX idx_audit_log_lookup ON audit_log(brand_id, entity_type, entity_id, timestamp DESC);
