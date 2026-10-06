-- The extension is enabled now so a later milestone can add an embedding column.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    category VARCHAR(255),
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    image_url TEXT,
    product_url TEXT,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_products_source_external_id UNIQUE (source, external_id)
);
