-- V14: Book Recommendations module (list / create / upvote / comments / top-recommenders)
-- Screens: recommendations list, share-a-book modal, trending + top-recommender panels.

CREATE TABLE IF NOT EXISTS recommendations (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  book_title        VARCHAR(255)    NOT NULL,
  author            VARCHAR(255)    NOT NULL,
  genre             VARCHAR(120)    NOT NULL DEFAULT 'General',
  rating            INTEGER         NOT NULL DEFAULT 5 CHECK (rating BETWEEN 1 AND 5),
  review_text       TEXT            NULL,
  spoiler           BOOLEAN         NOT NULL DEFAULT FALSE,
  tags              VARCHAR(1000)   NULL,
  cover_image_url   VARCHAR(1000)   NULL,
  recommended_by    UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  upvotes_count     INTEGER         NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_recommendations_created_at ON recommendations (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_recommendations_recommended_by ON recommendations (recommended_by);
CREATE INDEX IF NOT EXISTS ix_recommendations_genre ON recommendations (genre);

CREATE TABLE IF NOT EXISTS recommendation_upvotes (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  recommendation_id UUID            NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
  user_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_recommendation_upvote UNIQUE (recommendation_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_recommendation_upvote_rec ON recommendation_upvotes (recommendation_id);

CREATE TABLE IF NOT EXISTS recommendation_comments (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  recommendation_id UUID            NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
  author_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content           TEXT            NOT NULL,
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_recommendation_comments_rec ON recommendation_comments (recommendation_id, created_at ASC);
