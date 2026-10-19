-- V9: Ensure cover_image_url column exists on circles (idempotent guard)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'circles' AND column_name = 'cover_image_url'
    ) THEN
        ALTER TABLE circles ADD COLUMN cover_image_url TEXT;
    END IF;
END $$;

COMMENT ON COLUMN circles.cover_image_url IS 'Optional URL or media path for the reading circle cover banner image';
