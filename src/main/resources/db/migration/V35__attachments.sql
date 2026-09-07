-- Requirement #5: card attachments. Blocked until now because there was nowhere to put a file.
--
-- The bytes live in object storage; this table holds the metadata and, crucially, the brand
-- scope. The storage key is never accepted from a client — it is looked up from here — so a key
-- from one tenant's response cannot be replayed against another's.

CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,

    -- What the file hangs off. Kept generic (CARD, BRIEF, REPORT, CREATOR) so briefs and
    -- reports can attach files later without another table.
    owner_type VARCHAR(30) NOT NULL,
    owner_id UUID NOT NULL,

    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,

    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_attachment_owner CHECK (owner_type IN ('CARD', 'BRIEF', 'REPORT', 'CREATOR')),
    CONSTRAINT chk_attachment_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_attachments_owner ON attachments(owner_type, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_attachments_brand ON attachments(brand_id) WHERE deleted_at IS NULL;
