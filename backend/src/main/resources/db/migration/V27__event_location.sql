-- V27: Event location (Tamil Nadu district chosen by the organiser on create/edit).
ALTER TABLE events ADD COLUMN IF NOT EXISTS location VARCHAR(120) NULL;
