-- Marketing module (requirement #48) + authentication hardening.

-- Requirement #48: branded waitlist landing page with email capture that feeds the
-- creator database when the platform launches.
CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    handle VARCHAR(255),
    primary_platform VARCHAR(50),
    niche VARCHAR(255),
    source VARCHAR(100) NOT NULL DEFAULT 'LANDING_PAGE',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    consent_given BOOLEAN NOT NULL DEFAULT FALSE,
    confirm_token VARCHAR(128),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    converted_creator_id UUID REFERENCES creators(id) ON DELETE SET NULL,
    converted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_waitlist_email UNIQUE (email),
    CONSTRAINT chk_waitlist_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CONVERTED', 'REJECTED'))
);
CREATE INDEX idx_waitlist_status ON waitlist_entries(status);
CREATE INDEX idx_waitlist_token ON waitlist_entries(confirm_token);

-- Q-B7: refresh/reset tokens were found by loading every row and BCrypt-comparing each one.
-- A SHA-256 digest is deterministic, so it can be indexed and looked up directly.
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_digest VARCHAR(64);
ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS token_digest VARCHAR(64);

-- Existing rows cannot be migrated (the plaintext is unrecoverable from a BCrypt hash);
-- they are revoked so nobody is left holding a token the new lookup cannot find.
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL AND token_digest IS NULL;
UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE used_at IS NULL AND token_digest IS NULL;

CREATE UNIQUE INDEX uq_refresh_token_digest ON refresh_tokens(token_digest);
CREATE UNIQUE INDEX uq_reset_token_digest ON password_reset_tokens(token_digest);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Q-B10: brute-force protection. Recorded per identifier so lockout survives an IP change.
CREATE TABLE login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    ip_address VARCHAR(64),
    successful BOOLEAN NOT NULL DEFAULT FALSE,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_login_attempts_identifier ON login_attempts (lower(identifier), attempted_at DESC);
CREATE INDEX idx_login_attempts_ip ON login_attempts (ip_address, attempted_at DESC);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE;

-- Q-C11: roles become a closed set rather than free text.
UPDATE users SET role = 'ADMIN' WHERE role NOT IN ('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE');
ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE'));

CREATE INDEX idx_users_brand ON users(brand_id);

-- Brand profile fields that per-brand templating depends on (requirements #1, #4, #11, #50).
ALTER TABLE brands
    ADD COLUMN IF NOT EXISTS slug VARCHAR(100),
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS primary_colour VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tone_of_voice VARCHAR(50),
    ADD COLUMN IF NOT EXISTS brand_guidelines TEXT,
    ADD COLUMN IF NOT EXISTS instagram_handle VARCHAR(255),
    ADD COLUMN IF NOT EXISTS monitored_hashtags TEXT,
    ADD COLUMN IF NOT EXISTS reply_to_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS from_name VARCHAR(255);

UPDATE brands SET slug = 'default-brand', from_name = 'Generation B', tone_of_voice = 'CONVERSATIONAL'
WHERE id = '11111111-1111-1111-1111-111111111111' AND slug IS NULL;

-- Additional demo brands so the client can see the multi-brand model (each user still
-- belongs to exactly one brand, per Q-C13).
INSERT INTO brands (id, name, slug, tone_of_voice, from_name, instagram_handle) VALUES
    ('11111111-2222-2222-2222-111111111111', 'Mediheal', 'mediheal', 'PLAYFUL', 'Mediheal x B.', '@medihealuk'),
    ('11111111-3333-3333-3333-111111111111', 'Katie Loxton', 'katie-loxton', 'CONVERSATIONAL', 'Katie Loxton x B.', '@katieloxton')
ON CONFLICT (id) DO NOTHING;
