-- V13: Book Discussions module (list / create / view / threads / comments / likes)
-- Screens: discussions list, create form, view threads, contribute box.

CREATE TABLE IF NOT EXISTS discussions (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  book_title        VARCHAR(255)    NOT NULL,
  author            VARCHAR(255)    NOT NULL,
  genre             VARCHAR(120)    NOT NULL DEFAULT 'General',
  page_count        INTEGER         NULL,
  publication_year  INTEGER         NULL,
  cover_image_url   VARCHAR(1000)   NULL,
  key_quote         TEXT            NULL,
  quote_author      VARCHAR(255)    NULL,
  discussion_title  VARCHAR(255)    NOT NULL,
  discussion_excerpt TEXT           NULL,
  tag               VARCHAR(120)    NULL,
  created_by        UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  views_count       INTEGER         NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_discussions_created_at ON discussions (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_discussions_created_by ON discussions (created_by);
CREATE INDEX IF NOT EXISTS ix_discussions_genre ON discussions (genre);

CREATE TABLE IF NOT EXISTS discussion_threads (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  discussion_id UUID            NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  title         VARCHAR(255)    NOT NULL DEFAULT 'Community Contribution',
  content       TEXT            NOT NULL,
  author_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  likes_count   INTEGER         NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_discussion_threads_discussion ON discussion_threads (discussion_id, created_at ASC);

CREATE TABLE IF NOT EXISTS discussion_comments (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  thread_id   UUID            NOT NULL REFERENCES discussion_threads(id) ON DELETE CASCADE,
  author_id   UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content     TEXT            NOT NULL,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_discussion_comments_thread ON discussion_comments (thread_id, created_at ASC);

CREATE TABLE IF NOT EXISTS discussion_thread_likes (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  thread_id   UUID            NOT NULL REFERENCES discussion_threads(id) ON DELETE CASCADE,
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_discussion_thread_like UNIQUE (thread_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_discussion_thread_like_thread ON discussion_thread_likes (thread_id);
