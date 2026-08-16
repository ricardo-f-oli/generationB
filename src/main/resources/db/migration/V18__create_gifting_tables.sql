CREATE TABLE gifting_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    postal_code VARCHAR(50) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'UK',
    gdpr_consent_flag BOOLEAN NOT NULL DEFAULT TRUE,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gifting_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campaign_id UUID REFERENCES campaigns(id),
    brand_id UUID NOT NULL REFERENCES brands(id),
    mailer_text TEXT,
    comp_slip_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dispatches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    gifting_run_id UUID REFERENCES gifting_runs(id),
    creator_id UUID NOT NULL REFERENCES creators(id),
    tracking_number VARCHAR(100),
    courier VARCHAR(100) DEFAULT 'Royal Mail',
    status VARCHAR(50) NOT NULL DEFAULT 'READY_TO_DISPATCH',
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    return_reason VARCHAR(255)
);
