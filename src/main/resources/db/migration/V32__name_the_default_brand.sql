-- The seeded workspace brand was called "Default Brand", while the UI header says
-- "B. The Agency". Nobody noticed until the AI started writing copy from the brand record:
-- outreach emails opened "Collaboration with default brand" and clipping names came out as
-- "default-brand_davidtech_instagram_reel_...".
--
-- The brand name is customer-facing in three places now (AI copy, clipping names, report
-- headers), so it is renamed to match what the agency is actually called. Only the placeholder
-- row is touched; Mediheal and Katie Loxton are real brands and are left alone.

UPDATE brands
SET name = 'B. The Agency',
    slug = 'b-the-agency'
WHERE id = '11111111-1111-1111-1111-111111111111'
  AND name = 'Default Brand';
