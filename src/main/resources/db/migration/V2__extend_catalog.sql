-- Preserve legacy records; unknown metadata remains unknown until refreshed.
ALTER TABLE products
 ADD COLUMN brand VARCHAR(255),
 ADD COLUMN retailer VARCHAR(255),
 ADD COLUMN outfit_slot VARCHAR(30),
 ADD COLUMN color VARCHAR(80),
 ADD COLUMN available_sizes TEXT,
 ADD COLUMN availability VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
 ADD COLUMN last_seen_at TIMESTAMPTZ,
 ADD COLUMN style_tags TEXT,
 ADD COLUMN occasion_tags TEXT;
CREATE INDEX idx_products_currency_price ON products(currency, price);
CREATE INDEX idx_products_slot ON products(outfit_slot);
CREATE INDEX idx_products_last_seen ON products(last_seen_at);
