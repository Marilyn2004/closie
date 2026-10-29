ALTER TABLE studio_jobs ADD COLUMN request_key varchar(64);
CREATE INDEX studio_jobs_owner_request ON studio_jobs(owner,request_key,state);
-- Financial reservations intentionally have no cascading FK: deleting an item/job cannot reset budget.
CREATE TABLE studio_image_spend (
 id uuid PRIMARY KEY, job_id uuid NOT NULL UNIQUE, owner varchar(64) NOT NULL,
 provider varchar(20) NOT NULL, model varchar(100) NOT NULL,
 reserved_usd numeric(10,6) NOT NULL CHECK(reserved_usd>0),
 estimated_usd numeric(10,6), usage jsonb,
 status varchar(16) NOT NULL CHECK(status IN ('RESERVED','DISPATCHED','COMPLETED','UNKNOWN')),
 request_id varchar(160), created_at timestamptz NOT NULL DEFAULT now()
);
