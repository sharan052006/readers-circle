-- V25: Cover image columns hold uploaded data-URLs (can exceed 1000 chars).
-- Widen them to TEXT so Add/Edit Book (and circle/event/discussion/
-- recommendation covers) stop failing with 409 "conflict".

ALTER TABLE store_books ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE store_order_items ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE circles ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE events ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE discussions ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE recommendations ALTER COLUMN cover_image_url TYPE TEXT;
