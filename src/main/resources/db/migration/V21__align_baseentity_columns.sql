-- BaseEntity declares brand_id / created_at / updated_at / deleted_at on every entity that
-- extends it. Several tables were missing updated_at and deleted_at, which made
-- `ddl-auto: validate` fail on boot. Q-A3: keep extending BaseEntity, add the columns.

ALTER TABLE coverage_items
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE gifting_runs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

-- Dispatch and GiftingAddress become BaseEntity entities so they are brand-scoped and
-- soft-deletable like everything else (Q-C2: dispatches were modifiable across tenants).
ALTER TABLE dispatches
    ADD COLUMN IF NOT EXISTS brand_id UUID,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE dispatches d
SET brand_id = COALESCE(
        (SELECT r.brand_id FROM gifting_runs r WHERE r.id = d.gifting_run_id),
        '11111111-1111-1111-1111-111111111111'
    )
WHERE d.brand_id IS NULL;

ALTER TABLE dispatches ALTER COLUMN brand_id SET NOT NULL;
ALTER TABLE dispatches ADD CONSTRAINT fk_dispatches_brand FOREIGN KEY (brand_id) REFERENCES brands(id);

-- Q-J19: a viral TikTok can exceed the INT range.
ALTER TABLE coverage_items
    ALTER COLUMN views TYPE BIGINT,
    ALTER COLUMN likes TYPE BIGINT,
    ALTER COLUMN comments TYPE BIGINT;

-- Q-D8: digest settings had no audit columns.
ALTER TABLE coverage_digest_settings
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/London',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Q-J12: campaign_cards.creator_id had no foreign key.
ALTER TABLE campaign_cards
    ADD CONSTRAINT fk_campaign_cards_creator FOREIGN KEY (creator_id) REFERENCES creators(id);
