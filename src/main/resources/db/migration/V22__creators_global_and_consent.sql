-- Q-C6 / Q-C13: creators become GLOBAL to the agency (one row per human), while participation
-- per client brand is tracked explicitly. This is what makes cross-brand duplicate detection
-- and cross-brand send history possible at all.

CREATE TABLE creator_brand_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    relationship_status VARCHAR(30) NOT NULL DEFAULT 'PROSPECT',
    first_engaged_at TIMESTAMP WITH TIME ZONE,
    last_engaged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_creator_brand UNIQUE (creator_id, brand_id),
    CONSTRAINT chk_relationship_status CHECK (relationship_status IN ('PROSPECT', 'CONTACTED', 'WORKED_WITH', 'BLOCKED'))
);

CREATE INDEX idx_creator_brand_links_creator ON creator_brand_links(creator_id);
CREATE INDEX idx_creator_brand_links_brand ON creator_brand_links(brand_id);

-- Carry the existing single-brand assignment across before dropping the column.
INSERT INTO creator_brand_links (creator_id, brand_id, relationship_status, first_engaged_at, last_engaged_at)
SELECT id, brand_id, 'PROSPECT', created_at, created_at
FROM creators
WHERE brand_id IS NOT NULL
ON CONFLICT (creator_id, brand_id) DO NOTHING;

ALTER TABLE creators DROP COLUMN brand_id;

-- A creator is now globally unique by handle (Q-C6: duplicates were previously possible).
UPDATE creators c
SET handle = c.handle || '-' || substr(c.id::text, 1, 8)
WHERE EXISTS (
    SELECT 1 FROM creators o WHERE lower(o.handle) = lower(c.handle) AND o.id < c.id
);

CREATE UNIQUE INDEX uq_creators_handle ON creators (lower(handle));
CREATE INDEX idx_creators_email ON creators (lower(email));
CREATE INDEX idx_creators_niche ON creators (niche);
CREATE INDEX idx_creators_location ON creators (location);
CREATE INDEX idx_creators_opt_in_status ON creators (opt_in_status);
CREATE INDEX idx_creators_followers ON creators (followers_count);

-- Creator-facing fields that the registration form already collected but the backend discarded
-- (Q-20 / Q-F23 / Q-I5): consent, bio, portfolio, socials, follower band.
ALTER TABLE creators
    ADD COLUMN IF NOT EXISTS tiktok_handle VARCHAR(255),
    ADD COLUMN IF NOT EXISTS youtube_handle VARCHAR(255),
    ADD COLUMN IF NOT EXISTS follower_band VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bio TEXT,
    ADD COLUMN IF NOT EXISTS portfolio_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS uk_audience_pct NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS audience_age_band VARCHAR(50),
    ADD COLUMN IF NOT EXISTS audience_gender_split VARCHAR(50),
    ADD COLUMN IF NOT EXISTS quality_band VARCHAR(20),
    ADD COLUMN IF NOT EXISTS anonymised_at TIMESTAMP WITH TIME ZONE;

-- Q-I5 / Q-F23: consent must be captured explicitly with a lawful basis, timestamp and source.
CREATE TABLE consent_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID REFERENCES creators(id) ON DELETE CASCADE,
    brand_id UUID REFERENCES brands(id) ON DELETE SET NULL,
    subject_email VARCHAR(255),
    consent_type VARCHAR(50) NOT NULL,
    lawful_basis VARCHAR(50) NOT NULL,
    policy_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    granted BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(50) NOT NULL,
    source_ip VARCHAR(64),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_consent_type CHECK (consent_type IN ('MARKETING_EMAIL', 'DATA_STORAGE', 'GIFTING_ADDRESS')),
    CONSTRAINT chk_lawful_basis CHECK (lawful_basis IN ('CONSENT', 'LEGITIMATE_INTEREST', 'CONTRACT'))
);

CREATE INDEX idx_consent_records_creator ON consent_records(creator_id);
CREATE INDEX idx_consent_records_email ON consent_records(lower(subject_email));

-- Q-16 / Q-J35: admins define WHAT is tracked per creator; the existing table only stored values.
CREATE TABLE custom_attribute_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    attribute_key VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    attribute_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    options JSONB,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attr_def_brand_key UNIQUE (brand_id, attribute_key),
    CONSTRAINT chk_attr_type CHECK (attribute_type IN ('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT'))
);

ALTER TABLE creator_custom_attributes
    ADD COLUMN IF NOT EXISTS definition_id UUID REFERENCES custom_attribute_definitions(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS brand_id UUID REFERENCES brands(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_creator_custom_attrs_creator ON creator_custom_attributes(creator_id);

-- Q-17: tag names were globally unique, which contradicts "configurable per brand".
ALTER TABLE content_style_tags DROP CONSTRAINT IF EXISTS content_style_tags_name_key;
ALTER TABLE content_style_tags
    ADD COLUMN IF NOT EXISTS category VARCHAR(30) NOT NULL DEFAULT 'AESTHETIC',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE content_style_tags SET brand_id = '11111111-1111-1111-1111-111111111111' WHERE brand_id IS NULL;
ALTER TABLE content_style_tags ALTER COLUMN brand_id SET NOT NULL;
CREATE UNIQUE INDEX uq_style_tag_brand_name ON content_style_tags (brand_id, lower(name));

-- Q-18: notes need edit history and brand attribution.
ALTER TABLE creator_notes
    ADD COLUMN IF NOT EXISTS brand_id UUID REFERENCES brands(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
UPDATE creator_notes SET brand_id = '11111111-1111-1111-1111-111111111111' WHERE brand_id IS NULL;
ALTER TABLE creator_notes ALTER COLUMN brand_id SET NOT NULL;
CREATE INDEX idx_creator_notes_creator ON creator_notes(creator_id);

CREATE TABLE creator_note_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id UUID NOT NULL REFERENCES creator_notes(id) ON DELETE CASCADE,
    previous_text TEXT NOT NULL,
    edited_by UUID REFERENCES users(id),
    edited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_note_revisions_note ON creator_note_revisions(note_id);

-- Q-19: send history is the backbone of cross-brand duplicate detection.
ALTER TABLE creator_send_history
    ADD COLUMN IF NOT EXISTS send_type VARCHAR(30) NOT NULL DEFAULT 'OUTREACH',
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reference_id UUID;
CREATE INDEX idx_send_history_creator ON creator_send_history(creator_id);
CREATE INDEX idx_send_history_brand ON creator_send_history(brand_id);

-- Q-21: opt-out must be reachable by the creator, which means an unguessable token.
ALTER TABLE global_suppression_list
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS brand_id UUID REFERENCES brands(id) ON DELETE SET NULL;
CREATE INDEX idx_suppression_email ON global_suppression_list (lower(email));
CREATE INDEX idx_suppression_handle ON global_suppression_list (lower(handle));
CREATE INDEX idx_suppression_creator ON global_suppression_list (creator_id);
