-- V1: auth-identity users table (SPEC-auth-identity.md contracts)
-- Role is VARCHAR + CHECK (not PG enum type) to keep JPA + Flyway portable.
-- Email uniqueness is case-insensitive via LOWER(email) index (spec boundary).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(100)    NOT NULL CHECK (char_length(btrim(name)) > 0),
  email         VARCHAR(255)    NOT NULL CHECK (char_length(btrim(email)) > 0),
  password_hash VARCHAR(255)    NOT NULL,
  role          VARCHAR(20)     NOT NULL DEFAULT 'READER'
                CHECK (role IN ('ADMIN', 'ORGANIZER', 'READER')),
  active        BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: 'A@x.com' vs 'a@X.com' collide (spec: 409).
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
CREATE INDEX ix_users_role ON users (role);
