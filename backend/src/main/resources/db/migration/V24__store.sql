-- V24: Reader's Circle bookstore (admin Books/Orders + reader Books/Cart/Checkout).
-- sellable books, per-reader cart, delivery addresses, orders with timeline + payment snapshot.

CREATE TABLE IF NOT EXISTS store_books (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  title             VARCHAR(255)    NOT NULL,
  author            VARCHAR(255)    NOT NULL,
  description       TEXT            NULL,
  cover_image_url   VARCHAR(1000)   NULL,
  price_rupees      INTEGER         NOT NULL DEFAULT 0,
  stock_qty         INTEGER         NOT NULL DEFAULT 0,
  status            VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_store_books_title ON store_books (title);
CREATE INDEX IF NOT EXISTS ix_store_books_status ON store_books (status);
CREATE INDEX IF NOT EXISTS ix_store_books_created ON store_books (created_at DESC);

CREATE TABLE IF NOT EXISTS store_cart_items (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  book_id     UUID            NOT NULL REFERENCES store_books(id) ON DELETE CASCADE,
  qty         INTEGER         NOT NULL DEFAULT 1,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_store_cart_user_book UNIQUE (user_id, book_id)
);
CREATE INDEX IF NOT EXISTS ix_store_cart_user ON store_cart_items (user_id);

CREATE TABLE IF NOT EXISTS store_addresses (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label       VARCHAR(40)     NOT NULL DEFAULT 'Home',
  full_name   VARCHAR(120)    NOT NULL,
  phone       VARCHAR(20)     NOT NULL,
  line1       VARCHAR(255)    NOT NULL,
  line2       VARCHAR(255)    NULL,
  city        VARCHAR(120)    NOT NULL,
  state       VARCHAR(120)    NOT NULL,
  pincode     VARCHAR(12)     NOT NULL,
  is_default  BOOLEAN         NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_store_addresses_user ON store_addresses (user_id);

CREATE TABLE IF NOT EXISTS store_orders (
  id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  order_number      VARCHAR(20)     NOT NULL UNIQUE,
  user_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
  subtotal_rupees   INTEGER         NOT NULL DEFAULT 0,
  shipping_rupees   INTEGER         NOT NULL DEFAULT 0,
  total_rupees      INTEGER         NOT NULL DEFAULT 0,
  payment_method    VARCHAR(20)     NOT NULL DEFAULT 'UPI',
  payment_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
  transaction_id    VARCHAR(60)     NULL,
  paid_at           TIMESTAMPTZ     NULL,
  ship_name         VARCHAR(120)    NOT NULL,
  ship_phone        VARCHAR(20)     NOT NULL,
  ship_line1        VARCHAR(255)    NOT NULL,
  ship_line2        VARCHAR(255)    NULL,
  ship_city         VARCHAR(120)    NOT NULL,
  ship_state        VARCHAR(120)    NOT NULL,
  ship_pincode      VARCHAR(12)     NOT NULL,
  note              VARCHAR(500)    NULL,
  tracking_id       VARCHAR(80)     NULL,
  pending_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
  confirmed_at      TIMESTAMPTZ     NULL,
  shipped_at        TIMESTAMPTZ     NULL,
  delivered_at      TIMESTAMPTZ     NULL,
  cancelled_at      TIMESTAMPTZ     NULL,
  created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_store_orders_user ON store_orders (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_store_orders_status ON store_orders (status);
CREATE INDEX IF NOT EXISTS ix_store_orders_created ON store_orders (created_at DESC);

CREATE TABLE IF NOT EXISTS store_order_items (
  id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        UUID            NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
  book_id         UUID            NULL REFERENCES store_books(id) ON DELETE SET NULL,
  title           VARCHAR(255)    NOT NULL,
  author          VARCHAR(255)    NOT NULL,
  cover_image_url VARCHAR(1000)   NULL,
  price_rupees    INTEGER         NOT NULL DEFAULT 0,
  qty             INTEGER         NOT NULL DEFAULT 1,
  line_total      INTEGER         NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_store_order_items_order ON store_order_items (order_id);

-- Per-year order number counter (RC-2026-0001, ...). Bumped inside the order transaction.
CREATE TABLE IF NOT EXISTS store_order_counters (
  yr          INTEGER PRIMARY KEY,
  next_number INTEGER NOT NULL DEFAULT 1
);
