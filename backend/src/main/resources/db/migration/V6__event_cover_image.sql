-- V6: Ensure cover_image_url column exists on events (idempotent guard)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'events' AND column_name = 'cover_image_url'
    ) THEN
        ALTER TABLE events ADD COLUMN cover_image_url TEXT;
    END IF;
END $$;

COMMENT ON COLUMN events.cover_image_url IS 'Optional URL or media path for the event cover banner image';
