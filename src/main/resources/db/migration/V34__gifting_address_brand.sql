-- The public address form said "Generation B" because the brand name was hard-coded — the
-- address row had no way to know which brand had asked for it.
--
-- This matters beyond cosmetics: the consent the creator gives on that form is worded "I am
-- happy for <brand> to store this address". Consent given to a named brand has to record which
-- brand, or it is not the consent we claimed to collect.

ALTER TABLE gifting_addresses
    ADD COLUMN IF NOT EXISTS brand_id UUID REFERENCES brands(id) ON DELETE SET NULL;

-- Backfill from the campaign the address was requested for, where there is one.
UPDATE gifting_addresses a
SET brand_id = c.brand_id
FROM campaigns c
WHERE a.campaign_id = c.id AND a.brand_id IS NULL;

-- Anything left is pre-existing seed data belonging to the original workspace brand.
UPDATE gifting_addresses
SET brand_id = '11111111-1111-1111-1111-111111111111'
WHERE brand_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_gifting_addresses_brand ON gifting_addresses(brand_id);
