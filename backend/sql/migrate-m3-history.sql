-- Stop ALL backend writers and back up the database before running.
-- DDL commits implicitly. Never modify existing snapshots or current documents.
USE dbclasshw;
CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    content_format VARCHAR(16) NOT NULL,
    editor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_type VARCHAR(8) NOT NULL,
    source_version INT,
    CONSTRAINT uk_versions_document_number UNIQUE (document_id,version_number),
    INDEX idx_versions_editor (editor_id),
    CONSTRAINT ck_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_versions_format CHECK (content_format IN ('PLAIN_TEXT','TIPTAP_JSON')),
    CONSTRAINT ck_versions_change CHECK (change_type IN ('CREATED','UPDATED','RESTORED','BASELINE')),
    CONSTRAINT ck_versions_source CHECK ((change_type='RESTORED' AND source_version IS NOT NULL AND source_version > 0 AND source_version < version_number) OR (change_type<>'RESTORED' AND source_version IS NULL)),
    CONSTRAINT fk_versions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_versions_editor FOREIGN KEY (editor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_m3_history$$
CREATE PROCEDURE migrate_m3_history()
BEGIN
    IF EXISTS (SELECT 1 FROM documents d LEFT JOIN users u ON u.id=d.owner_id
               WHERE d.version<1 OR u.id IS NULL OR d.content_format NOT IN ('PLAIN_TEXT','TIPTAP_JSON')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected document state; stop and inspect';
    END IF;
    IF EXISTS (SELECT 1 FROM document_versions v JOIN documents d ON d.id=v.document_id
               WHERE v.version_number>d.version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='History ahead of current document; stop and inspect';
    END IF;
    IF EXISTS (SELECT 1 FROM documents d WHERE EXISTS (SELECT 1 FROM document_versions v WHERE v.document_id=d.id)
               AND NOT EXISTS (SELECT 1 FROM document_versions v WHERE v.document_id=d.id AND v.version_number=d.version)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incomplete existing history; stop and inspect';
    END IF;
    IF EXISTS (SELECT 1 FROM documents d JOIN document_versions v ON v.document_id=d.id AND v.version_number=d.version
               WHERE NOT (BINARY v.title <=> BINARY d.title) OR NOT (BINARY v.content <=> BINARY d.content)
               OR NOT (BINARY v.content_format <=> BINARY d.content_format)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Current snapshot mismatch; stop and inspect';
    END IF;
    -- The owner is a baseline custodian, not an assertion about the last editor.
    -- Timestamp is the existing document updated_at; no earlier history is invented.
    INSERT INTO document_versions(document_id,version_number,title,content,content_format,editor_id,created_at,change_type)
    SELECT d.id,d.version,d.title,d.content,d.content_format,d.owner_id,d.updated_at,'BASELINE'
    FROM documents d WHERE NOT EXISTS (SELECT 1 FROM document_versions v WHERE v.document_id=d.id);
END$$
CALL migrate_m3_history()$$
DROP PROCEDURE migrate_m3_history$$
DELIMITER ;
