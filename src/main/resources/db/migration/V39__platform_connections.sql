-- Moving off the paid creator-data vendor and onto the platforms' own APIs.
--
-- Two things need storing that did not before.
--
-- ---------------------------------------------------------------------------
-- 1. WHICH CREATORS HAVE CONNECTED AN ACCOUNT
--
-- Instagram and YouTube will only give audience demographics for an account whose owner has
-- authorised our app. That is a better trade than it sounds: the figures come from the platform
-- rather than a vendor's model, they cost nothing, and consent is explicit and revocable — which
-- is a cleaner GDPR position than buying a profile from a broker.
--
-- The cost is that it needs the creator to do something, so their connection state has to be
-- visible and chase-able rather than assumed.
--
-- TikTok is deliberately different. Connecting gets us a creator's videos and follower count,
-- and that is all: TikTok's open API exposes no audience demographics at any tier available to a
-- commercial agency. The column is here so TikTok content still works; nothing will ever write
-- demographics for it.

CREATE TABLE creator_platform_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,

    -- INSTAGRAM, TIKTOK or YOUTUBE.
    platform VARCHAR(20) NOT NULL,

    -- The platform's own id for the account, which survives a handle change. This is the thing
    -- worth keying on; a username is a display name that happens to be unique today.
    external_account_id VARCHAR(128) NOT NULL,
    external_username VARCHAR(255),

    -- Encrypted at rest, never logged, never returned by any endpoint. An OAuth access token is
    -- a live credential for somebody else's account: it has to be usable, so it cannot be hashed
    -- the way our own tokens are.
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT,

    -- What the creator actually granted. Stored because scopes get added over time and we need
    -- to know which connections predate a new one rather than assuming they all have it.
    granted_scopes VARCHAR(500),

    connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    last_refreshed_at TIMESTAMP WITH TIME ZONE,

    -- Set when the creator disconnects or withdraws consent. The row is kept rather than deleted
    -- so there is evidence the connection existed and when it ended; the tokens are cleared.
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_connection_platform
        CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'YOUTUBE'))
);

-- One live connection per creator per platform. Partial, so a revoked connection does not block
-- reconnecting later.
CREATE UNIQUE INDEX uq_creator_platform_live
    ON creator_platform_connections(creator_id, platform)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_connections_creator ON creator_platform_connections(creator_id);

-- "Who has not connected yet" is the question the chase list asks, and it is asked per platform.
CREATE INDEX idx_connections_platform_live
    ON creator_platform_connections(platform)
    WHERE revoked_at IS NULL;

COMMENT ON COLUMN creator_platform_connections.access_token_encrypted IS
    'AES-GCM, key from INSIGHTS_TOKEN_ENCRYPTION_KEY. Never logged or returned by an endpoint.';

-- ---------------------------------------------------------------------------
-- 2. THE INVITATION A CREATOR FOLLOWS TO CONNECT
--
-- Same shape as the gifting address capture link, deliberately: tokenised, single use, expiring.
-- That pattern already works, the team already understands it, and inventing a second mechanism
-- for the same job would be worse than reusing this one.

CREATE TABLE creator_connection_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    brand_id UUID REFERENCES brands(id) ON DELETE SET NULL,

    -- Unguessable. The creator is not signed in when they follow it, so the token is the only
    -- thing standing between this link and anyone who finds it.
    request_token VARCHAR(128) NOT NULL UNIQUE,

    -- Which platforms this invitation asks for, comma separated. A creator who is only on
    -- Instagram should not be asked to connect TikTok.
    requested_platforms VARCHAR(100) NOT NULL DEFAULT 'INSTAGRAM',

    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- How many times the link was opened, so a creator who tried and failed is distinguishable
    -- from one who never looked. They need different follow-up.
    opened_count INT NOT NULL DEFAULT 0,
    last_opened_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_connection_requests_creator ON creator_connection_requests(creator_id);
CREATE INDEX idx_connection_requests_open
    ON creator_connection_requests(expires_at)
    WHERE completed_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. THE HASHTAG ALLOWANCE
--
-- Instagram allows 30 UNIQUE hashtags per rolling 7 days, per app — not per brand. With several
-- client brands each monitoring their own tags plus a competitor or two, that is the binding
-- constraint on mention discovery, and it is shared across all of them.
--
-- Tracked in the database rather than in memory because the window is seven days and a restart
-- must not reset it. Re-querying a tag already inside the window is free, so this doubles as the
-- cache that makes repeat sweeps cost nothing.

CREATE TABLE instagram_hashtag_lookups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Normalised: lower case, no leading #.
    hashtag VARCHAR(150) NOT NULL,

    -- Meta's node id for the tag. Stable, so holding it avoids re-resolving.
    hashtag_id VARCHAR(64) NOT NULL,

    first_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    use_count INT NOT NULL DEFAULT 1,

    -- Which brand's monitoring first pulled this tag in, for working out who is using the
    -- shared allowance when it runs short.
    first_used_by_brand UUID REFERENCES brands(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_hashtag_lookup ON instagram_hashtag_lookups(hashtag);

-- The allowance question is "how many distinct tags in the last 7 days", which is this index
-- read backwards.
CREATE INDEX idx_hashtag_first_used ON instagram_hashtag_lookups(first_used_at DESC);

COMMENT ON TABLE instagram_hashtag_lookups IS
    'Instagram allows 30 unique hashtags per rolling 7 days per app, shared across every brand. '
    'This is both the budget ledger and the id cache that makes a repeat sweep free.';
