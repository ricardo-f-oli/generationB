-- Gifting workflow completion: requirements #41 and #43-#47.
--
-- The tables existed but the columns the workflow needs did not, and an address row could not
-- be created before the creator had filled it in (street/city/postcode were NOT NULL), which is
-- exactly the state requirement #41 needs to track: "asked, not yet answered".

ALTER TABLE gifting_addresses
    ALTER COLUMN street DROP NOT NULL,
    ALTER COLUMN city DROP NOT NULL,
    ALTER COLUMN postal_code DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS street2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS county VARCHAR(120),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMP WITH TIME ZONE;

-- One address per creator: the capture form upserts rather than accumulating history.
-- Keep the most recently consented row if any duplicates crept in before the constraint.
DELETE FROM gifting_addresses a
USING gifting_addresses b
WHERE a.creator_id = b.creator_id
  AND (a.consented_at, a.id) < (b.consented_at, b.id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gifting_address_creator
    ON gifting_addresses(creator_id);

-- Requirement #44: a comp slip is signed off by a person, at a time.
ALTER TABLE gifting_runs
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS approved_by UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE gifting_runs SET name = 'Gifting run' WHERE name IS NULL;

ALTER TABLE gifting_runs DROP CONSTRAINT IF EXISTS chk_comp_slip_status;
ALTER TABLE gifting_runs ADD CONSTRAINT chk_comp_slip_status
    CHECK (comp_slip_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Requirement #43: the brand order needs to know which run it is supplying.
ALTER TABLE brand_orders
    ADD COLUMN IF NOT EXISTS gifting_run_id UUID REFERENCES gifting_runs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_brand_orders_token ON brand_orders(confirm_token);
CREATE INDEX IF NOT EXISTS idx_dispatches_creator ON dispatches(creator_id);
CREATE INDEX IF NOT EXISTS idx_dispatches_deadline ON dispatches(content_deadline)
    WHERE status = 'DELIVERED';
