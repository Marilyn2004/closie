ALTER TABLE products ADD COLUMN ebay_owner_hash VARCHAR(64);
CREATE INDEX idx_products_ebay_owner ON products(ebay_owner_hash) WHERE source='ebay';
CREATE TABLE ebay_deleted_accounts (
 owner_hash CHAR(64) PRIMARY KEY,
 deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE ebay_catalog_control ADD COLUMN hash_key_fingerprint CHAR(64);
ALTER TABLE ebay_catalog_control ADD COLUMN pause_reason VARCHAR(40);
ALTER TABLE ebay_deletion_receipts ADD COLUMN handling_mode VARCHAR(30) NOT NULL DEFAULT 'LEGACY_PURGE';
-- This does not clear the pause. An authenticated review must recheck the empty catalog.
UPDATE ebay_catalog_control SET pause_reason=CASE
 WHEN NOT EXISTS(SELECT FROM products WHERE source='ebay')
  AND NOT EXISTS(SELECT FROM ebay_deletion_receipts WHERE purged_products>0)
 THEN 'LEGACY_EMPTY_REVIEW' ELSE 'LEGACY_REVIEW_REQUIRED' END
WHERE imports_paused=TRUE;
