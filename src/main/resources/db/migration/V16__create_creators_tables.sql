CREATE TABLE creators (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    name VARCHAR(255) NOT NULL,
    handle VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    primary_platform VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM',
    followers_count INT NOT NULL DEFAULT 0,
    er_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    location VARCHAR(255),
    niche VARCHAR(255),
    opt_in_status VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    opt_in_step INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE creator_custom_attributes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    attribute_key VARCHAR(255) NOT NULL,
    attribute_value TEXT,
    attribute_type VARCHAR(50) NOT NULL DEFAULT 'STRING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_style_tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand_id UUID REFERENCES brands(id),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE creator_style_tags (
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES content_style_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (creator_id, tag_id)
);

CREATE TABLE creator_notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    author_id UUID REFERENCES users(id),
    note_text TEXT NOT NULL,
    is_confidential BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE creator_send_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    brand_id UUID NOT NULL REFERENCES brands(id),
    campaign_id UUID REFERENCES campaigns(id),
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duplicate_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE global_suppression_list (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID REFERENCES creators(id) ON DELETE SET NULL,
    email VARCHAR(255),
    handle VARCHAR(255),
    reason VARCHAR(255) DEFAULT 'Opt-out requested',
    opted_out_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
