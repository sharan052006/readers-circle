-- V11: Category for reading circles (Circles admin UI: Tamil Literature, etc.)
ALTER TABLE circles ADD COLUMN IF NOT EXISTS category VARCHAR(100);
CREATE INDEX IF NOT EXISTS ix_circles_category_lower ON circles (LOWER(category));
COMMENT ON COLUMN circles.category IS 'Optional reading category shown in admin Circles UI (e.g. Tamil Literature)';
