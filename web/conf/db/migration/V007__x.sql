ALTER TABLE document_set ADD COLUMN title VARCHAR(511);
UPDATE document_set SET title = query;
ALTER TABLE document_set ALTER COLUMN title SET NOT NULL;

ALTER TABLE document_set_creation_job ADD COLUMN documentcloud_username VARCHAR(255);
ALTER TABLE document_set_creation_job ADD COLUMN documentcloud_password VARCHAR(255);
