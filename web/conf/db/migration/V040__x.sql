ALTER TABLE document DROP CONSTRAINT document_document_cloud_type_check;

ALTER TABLE document ADD CONSTRAINT document_document_cloud_type_check
  CHECK (type <> 'DocumentCloudDocument' OR
    (documentcloud_id IS NOT NULL
      AND url IS NULL
      AND supplied_id IS NULL));
