-- Shortlist tables. These were referenced by the Shortlist/ShortlistItem entities and by the
-- V19 seed, but no migration ever created them: Hibernate's `ddl-auto: validate` failed on boot.

CREATE TABLE shortlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    name VARCHAR(255) NOT NULL,
    campaign_id UUID REFERENCES campaigns(id),
    created_by UUID REFERENCES users(id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'TEAM',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_shortlist_visibility CHECK (visibility IN ('TEAM', 'PRIVATE'))
);

CREATE TABLE shortlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shortlist_id UUID NOT NULL REFERENCES shortlists(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    position INT NOT NULL DEFAULT 0,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shortlist_creator UNIQUE (shortlist_id, creator_id)
);

CREATE INDEX idx_shortlists_brand_id ON shortlists(brand_id);
CREATE INDEX idx_shortlist_items_shortlist_id ON shortlist_items(shortlist_id);
CREATE INDEX idx_shortlist_items_creator_id ON shortlist_items(creator_id);

-- Seed (moved here from V19, with valid UUID literals: 's…' -> '5a…', 'i…' -> '1a…')
INSERT INTO shortlists (id, brand_id, name, created_by, visibility)
VALUES
    ('5a111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Mediheal Spring Seeding Shortlist', '22222222-2222-2222-2222-222222222222', 'TEAM'),
    ('5a222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Katie Loxton Q3 VIP List', '22222222-2222-2222-2222-222222222222', 'PRIVATE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shortlist_items (id, shortlist_id, creator_id, position)
VALUES
    ('1a111111-1111-1111-1111-111111111111', '5a111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 0),
    ('1a222222-2222-2222-2222-222222222222', '5a111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222', 1),
    ('1a333333-3333-3333-3333-333333333333', '5a111111-1111-1111-1111-111111111111', 'c3333333-3333-3333-3333-333333333333', 2)
ON CONFLICT (id) DO NOTHING;
