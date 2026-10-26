CREATE TABLE catalog_import_runs (
 id BIGSERIAL PRIMARY KEY,
 started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at TIMESTAMPTZ,
 status VARCHAR(20) NOT NULL,
 created_count INTEGER NOT NULL DEFAULT 0,
 updated_count INTEGER NOT NULL DEFAULT 0,
 skipped_count INTEGER NOT NULL DEFAULT 0,
 error_code VARCHAR(80)
);
CREATE INDEX idx_catalog_import_runs_started ON catalog_import_runs(started_at DESC);
