CREATE TABLE coverage_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    campaign_id UUID REFERENCES campaigns(id),
    creator_id UUID REFERENCES creators(id),
    creator_handle VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM',
    post_type VARCHAR(50) NOT NULL DEFAULT 'REEL',
    url VARCHAR(500),
    views INT NOT NULL DEFAULT 0,
    likes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    er NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    standardized_name VARCHAR(255) NOT NULL,
    is_unsolicited BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coverage_digest_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID NOT NULL REFERENCES brands(id) UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    send_time VARCHAR(20) NOT NULL DEFAULT '08:00',
    recipient_email VARCHAR(255)
);
