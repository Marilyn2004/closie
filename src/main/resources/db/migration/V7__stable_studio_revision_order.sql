-- Preserve IDs and parent links; a timestamp alone cannot order two appends in one transaction.
CREATE SEQUENCE studio_revision_order_seq;
ALTER TABLE studio_revisions ADD COLUMN revision_order bigint;
WITH RECURSIVE ancestry AS (
 SELECT id,parent_id,created_at,0 AS depth FROM studio_revisions WHERE parent_id IS NULL
 UNION ALL
 SELECT child.id,child.parent_id,child.created_at,parent.depth+1
 FROM studio_revisions child JOIN ancestry parent ON child.parent_id=parent.id
), ordered AS (
 SELECT id,row_number() OVER(ORDER BY created_at,depth,id) AS ordinal FROM ancestry
)
UPDATE studio_revisions revision SET revision_order=ordered.ordinal FROM ordered WHERE ordered.id=revision.id;
SELECT setval('studio_revision_order_seq',COALESCE((SELECT max(revision_order)+1 FROM studio_revisions),1),false);
ALTER TABLE studio_revisions ALTER COLUMN revision_order SET DEFAULT nextval('studio_revision_order_seq');
ALTER TABLE studio_revisions ALTER COLUMN revision_order SET NOT NULL;
CREATE UNIQUE INDEX studio_revision_order_unique ON studio_revisions(revision_order);
