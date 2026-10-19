-- V29: Persist Admin platform settings in database (replacing in-memory storage)
CREATE TABLE IF NOT EXISTS admin_settings (
  id                            VARCHAR(50)     PRIMARY KEY,
  platform_name                 VARCHAR(255)    NOT NULL DEFAULT 'Reader''s Circle',
  tagline                       VARCHAR(500)    NOT NULL DEFAULT 'Build a stronger reading community',
  community_guidelines          TEXT            NOT NULL DEFAULT 'Be respectful, cite sources, encourage diverse literary viewpoints, and avoid spoilers without tags.',
  registration_open             BOOLEAN         NOT NULL DEFAULT TRUE,
  allow_member_circle_creation  BOOLEAN         NOT NULL DEFAULT FALSE,
  max_circles_per_member        INTEGER         NOT NULL DEFAULT 5,
  email_notifications_enabled   BOOLEAN         NOT NULL DEFAULT TRUE,
  custom_settings_json          TEXT            NULL,
  updated_at                    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

INSERT INTO admin_settings (
  id,
  platform_name,
  tagline,
  community_guidelines,
  registration_open,
  allow_member_circle_creation,
  max_circles_per_member,
  email_notifications_enabled,
  custom_settings_json,
  updated_at
) VALUES (
  'default',
  'Reader''s Circle',
  'Build a stronger reading community',
  'Be respectful, cite sources, encourage diverse literary viewpoints, and avoid spoilers without tags.',
  TRUE,
  FALSE,
  5,
  TRUE,
  NULL,
  now()
) ON CONFLICT (id) DO NOTHING;
