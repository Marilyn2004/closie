-- Additive studio storage; existing retail records and integration tables are untouched.
CREATE TABLE studio_documents (
 id uuid PRIMARY KEY, owner varchar(64) NOT NULL, kind varchar(16) NOT NULL
 CHECK(kind IN ('CONCEPT','OWNED','OUTFIT','PROFILE')),
 payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX studio_documents_owner_kind ON studio_documents(owner,kind);
CREATE TABLE studio_revisions (
 id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES studio_documents(id) ON DELETE CASCADE,
 parent_id uuid REFERENCES studio_revisions(id), payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX studio_revisions_document ON studio_revisions(document_id,created_at);
CREATE TABLE studio_assets (
 id uuid PRIMARY KEY, owner varchar(64) NOT NULL, file_name varchar(100) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE studio_jobs (
 id uuid PRIMARY KEY, owner varchar(64) NOT NULL, revision_id uuid NOT NULL REFERENCES studio_revisions(id) ON DELETE CASCADE,
 operation varchar(12) NOT NULL CHECK(operation IN ('GARMENT','DOLL')),
 state varchar(16) NOT NULL CHECK(state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
 input jsonb NOT NULL, result jsonb, attempts integer NOT NULL DEFAULT 0,
 error varchar(240), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX studio_jobs_queue ON studio_jobs(state,created_at);
CREATE INDEX studio_jobs_owner ON studio_jobs(owner,created_at);
CREATE TABLE studio_model_usage (
 id uuid PRIMARY KEY, owner varchar(64) NOT NULL, purpose varchar(20) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX studio_model_usage_time ON studio_model_usage(created_at);
