-- No account identifiers or raw notification payloads are retained.
CREATE TABLE ebay_catalog_control (
 id INTEGER PRIMARY KEY CHECK (id = 1),
 imports_paused BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO ebay_catalog_control(id) VALUES (1);
CREATE TABLE ebay_deletion_receipts (
 notification_hash CHAR(64) PRIMARY KEY,
 processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 purged_products INTEGER NOT NULL
);
