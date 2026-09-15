-- The attribute screen offers "Pick several", "Web address" and "Email", and AttributeType
-- validates all three, but V22's check constraint only allowed the first five types, so saving
-- one of those attributes failed with a constraint violation.
ALTER TABLE custom_attribute_definitions DROP CONSTRAINT IF EXISTS chk_attr_type;
ALTER TABLE custom_attribute_definitions ADD CONSTRAINT chk_attr_type
    CHECK (attribute_type IN ('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MULTI_SELECT', 'URL', 'EMAIL'));
