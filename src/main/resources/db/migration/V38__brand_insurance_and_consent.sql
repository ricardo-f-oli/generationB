-- Requirement #40 (legal disclaimer + insurance) and #20 (the five opt-in questions).
--
-- Both were left open for the client to decide. With that decision delegated back, these are the
-- choices taken and the reasoning, so a later reader can disagree with the reasoning rather than
-- guess at it.
--
-- ---------------------------------------------------------------------------
-- #40 INSURANCE
--
-- The question "is the agency insured" has a specific answer in influencer gifting, and it is
-- not one policy. Three risks, three owners:
--
--   * The product injures someone            -> the BRAND's product liability cover
--   * The agency gives bad advice            -> the agency's professional indemnity
--   * Someone is hurt on the agency's premises -> the agency's public liability
--
-- Only the first is per-campaign and only the first can be checked before an action. An agency
-- that posts a client's cosmetics to a stranger's home without confirming the client carries
-- product liability is the exposure worth engineering against, so that is what is modelled: the
-- cover is recorded per brand and dispatch is refused when it is absent or lapsed.
--
-- The agency's own policies are a fixed fact about the business, not per-brand data, so they
-- belong in the privacy notice and the MSA rather than in a database column that would never be
-- read.

ALTER TABLE brands
    ADD COLUMN IF NOT EXISTS product_liability_insurer VARCHAR(200),
    ADD COLUMN IF NOT EXISTS product_liability_policy_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS product_liability_expires_on DATE,
    ADD COLUMN IF NOT EXISTS product_liability_cover_gbp BIGINT,
    ADD COLUMN IF NOT EXISTS insurance_notes VARCHAR(1000),

    -- Printed on the comp slip and shown on the address form. Per brand because the product
    -- differs: cosmetics need an allergy line that a tote bag does not.
    ADD COLUMN IF NOT EXISTS gifting_disclaimer TEXT;

COMMENT ON COLUMN brands.product_liability_expires_on IS
    'Requirement #40. Gifting dispatch is refused when this is null or in the past. A lapsed '
    'policy is the case that matters: it looks exactly like a valid one until someone checks.';

-- The nightly warning and the settings screen both ask "which brands are about to lapse".
CREATE INDEX IF NOT EXISTS idx_brands_insurance_expiry
    ON brands(product_liability_expires_on)
    WHERE deleted_at IS NULL;

-- A default disclaimer for every brand that has not written its own. Deliberately short: a
-- creator reads a comp slip, they do not read terms.
--
-- The "no obligation" sentence is the load-bearing one. Under the CAP Code a gift only becomes
-- an advertisement the creator must disclose if there is an agreement to post; saying plainly
-- that there is none keeps a genuine gift a genuine gift, and protects the creator from
-- accidentally breaching the rules.
UPDATE brands
   SET gifting_disclaimer =
       'This is a gift, sent with no obligation to post. If you do share it, please mark the '
       'post as a gift so your audience knows. Please check the ingredients or materials list '
       'before use if you have any allergies. Any issue with the product itself is the '
       'responsibility of the brand that made it, and we will put you in touch.'
 WHERE gifting_disclaimer IS NULL;

-- ---------------------------------------------------------------------------
-- #20 THE FIVE OPT-IN QUESTIONS
--
-- The registration form asked one blanket "I consent" box. UK GDPR wants consent to be granular
-- and specific: one tick covering storage, marketing, address sharing and content reuse is not
-- freely given consent for any of them, and it is unusable as evidence.
--
-- Five questions, one per genuine processing purpose. Only the first is required, because it is
-- the only one without which there is nothing to store:
--
--   1. DATA_STORAGE   keep my details on file                  (required)
--   2. MARKETING_EMAIL contact me about campaigns              (optional)
--   3. GIFTING_ADDRESS send me product, address shared with the fulfilment house (optional)
--   4. BRAND_SHARING  share my profile with client brands      (optional)
--   5. CONTENT_REUSE  use my posts in reports and marketing    (optional)
--
-- 3 and 4 are separated on purpose: they are the two that involve a third party seeing personal
-- data, and they are the two a creator is most likely to want to answer differently.

ALTER TABLE consent_records DROP CONSTRAINT IF EXISTS chk_consent_type;

ALTER TABLE consent_records ADD CONSTRAINT chk_consent_type
    CHECK (consent_type IN (
        'DATA_STORAGE',
        'MARKETING_EMAIL',
        'GIFTING_ADDRESS',
        'BRAND_SHARING',
        'CONTENT_REUSE'
    ));

-- Answers live on the creator too, so a screen can show them without aggregating the evidence
-- table on every read. consent_records stays the audit trail: it records every grant and
-- withdrawal with a timestamp, and it is what proves consent was obtained.
ALTER TABLE creators
    ADD COLUMN IF NOT EXISTS consent_marketing_email BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS consent_gifting_address BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS consent_brand_sharing BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS consent_content_reuse BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN creators.consent_marketing_email IS
    'Requirement #20/#21. False blocks outreach regardless of the suppression list.';

-- Creators already on file registered under the single blanket box, which did cover storage and
-- being contacted about campaigns — that was what the old form said. It did not mention sharing
-- profiles with client brands or reusing content, so those two stay false and have to be asked
-- again rather than assumed. Assuming consent nobody gave is the failure this whole change is
-- about.
UPDATE creators
   SET consent_marketing_email = TRUE,
       consent_gifting_address = TRUE
 WHERE opt_in_status = 'APPROVED'
   AND anonymised_at IS NULL;
