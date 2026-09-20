-- V2: circles-membership tables (SPEC-circles-membership.md contracts)

CREATE TABLE circles (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(150)    NOT NULL CHECK (char_length(btrim(name)) > 0),
  city          VARCHAR(100)    NOT NULL CHECK (char_length(btrim(city)) > 0),
  description   TEXT            NOT NULL,
  organizer_id  UUID            NOT NULL REFERENCES users(id),
  status        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'INACTIVE')),
  created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_circles_city_lower ON circles (LOWER(city));
CREATE INDEX ix_circles_status ON circles (status);
CREATE INDEX ix_circles_organizer ON circles (organizer_id);

CREATE TABLE memberships (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  circle_id     UUID            NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  reader_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  requested_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  decided_at    TIMESTAMPTZ,
  CONSTRAINT ux_memberships_circle_reader UNIQUE (circle_id, reader_id)
);

CREATE INDEX ix_memberships_circle_status ON memberships (circle_id, status);
CREATE INDEX ix_memberships_reader ON memberships (reader_id);
