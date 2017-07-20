-- Add a job_id column to tree so that a client that's monitoring viz jobs
-- can determine what tree the job transforms into.
--
-- We'll set the existing job_ids to 0, because the client can't handle them
-- anyway.
ALTER TABLE tree ADD COLUMN job_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tree ALTER COLUMN job_id DROP DEFAULT;
CREATE INDEX tree_job_id ON tree (job_id);

-- One could argue that our existing job-to-tree table, which helps handle job
-- cancellation, serves a different purpose. But it seems excessive to store
-- the mapping twice in the same database.
--
-- Don't evolve if there are any recluster jobs running....
DROP TABLE document_set_creation_job_tree;
