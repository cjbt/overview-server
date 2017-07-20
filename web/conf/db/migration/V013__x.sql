CREATE TABLE uploaded_file (
  id                        BIGSERIAL PRIMARY KEY,
  uploaded_at               TIMESTAMP NOT NULL,
  contents_oid              OID NOT NULL, 
  content_disposition       VARCHAR NOT NULL,
  content_type              VARCHAR NOT NULL,		
  size                      BIGINT NOT NULL
);

CREATE TABLE upload (
  id                        BIGSERIAL PRIMARY KEY,
  user_id                   BIGINT NOT NULL REFERENCES "user" (id),
  guid                      UUID NOT NULL,
  uploaded_file_id          BIGINT NOT NULL REFERENCES uploaded_file (id),
  last_activity             TIMESTAMP NOT NULL,
  total_size                BIGINT NOT NULL
);

CREATE TYPE document_set_type AS ENUM ('CsvImportDocumentSet', 'DocumentCloudDocumentSet');
ALTER TABLE document_set ADD COLUMN type document_set_type NOT NULL DEFAULT 'DocumentCloudDocumentSet'::document_set_type;
ALTER TABLE document_set ALTER COLUMN type DROP DEFAULT;
ALTER TABLE document_set ADD COLUMN uploaded_file_id BIGINT REFERENCES uploaded_file (id);
ALTER TABLE document_set ALTER COLUMN query DROP NOT NULL;
ALTER TABLE document_set ADD CONSTRAINT document_set_document_cloud_type_check 
  CHECK (type <> 'DocumentCloudDocumentSet' OR 
    (query IS NOT NULL AND uploaded_file_id IS NULL));
ALTER TABLE document_set ADD CONSTRAINT document_set_csv_import_type_check
   CHECK (type <> 'CsvImportDocumentSet' OR
     (query IS NULL AND uploaded_file_id IS NOT NULL));

CREATE TYPE document_type AS ENUM ('DocumentCloudDocument', 'CsvImportDocument');
ALTER TABLE document ADD COLUMN type document_type NOT NULL DEFAULT 'DocumentCloudDocument'::document_type;
ALTER TABLE document ALTER COLUMN type DROP DEFAULT;
ALTER TABLE document ADD COLUMN text VARCHAR;
ALTER TABLE document ADD COLUMN url VARCHAR;
ALTER TABLE document ADD COLUMN supplied_id VARCHAR;
ALTER TABLE document ALTER COLUMN documentcloud_id DROP NOT NULL;
ALTER TABLE document ADD CONSTRAINT document_document_cloud_type_check
  CHECK (type <> 'DocumentCloudDocument' OR
    (documentcloud_id IS NOT NULL
      AND text IS NULL
      AND url IS NULL
      AND supplied_id IS NULL));
ALTER TABLE document ADD CONSTRAINT document_csv_import_type_check
  CHECK (type <> 'CsvImportDocument' OR
    (documentcloud_id IS NULL
      AND text IS NOT NULL));
