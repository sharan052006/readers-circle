-- V10: Add profile fields to users table (bio and profile_picture_url)
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_picture_url VARCHAR(1000);
