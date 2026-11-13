-- Stop backend writers and save a database backup before running.
-- MySQL DDL commits implicitly. Re-run resumes safe completed steps; never drops business data.
USE dbclasshw;
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_m1_m2$$
CREATE PROCEDURE migrate_m1_m2()
BEGIN
    DECLARE migration_user BIGINT;
    DECLARE original_count BIGINT;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='documents' AND COLUMN_NAME IN ('id','title','content','version','created_at','updated_at')) <> 6 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected documents schema; stop and inspect';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='documents') NOT IN (6,7) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected document columns; stop and inspect';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username='__m1_migration__'
               AND (enabled<>FALSE OR password_hash<>'!disabled:m1-migration' OR email<>'m1-migration@invalid.local')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reserved migration account conflict';
    END IF;
    INSERT INTO users(username,email,password_hash,enabled)
    SELECT '__m1_migration__','m1-migration@invalid.local','!disabled:m1-migration',FALSE
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='__m1_migration__');
    SELECT id INTO migration_user FROM users WHERE username='__m1_migration__';
    SELECT COUNT(*) INTO original_count FROM documents;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                   AND TABLE_NAME='documents' AND COLUMN_NAME='owner_id') THEN
        ALTER TABLE documents ADD COLUMN owner_id BIGINT NULL AFTER id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
               AND TABLE_NAME='documents' AND COLUMN_NAME='owner_id' AND COLUMN_TYPE<>'bigint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected owner_id type';
    END IF;
    -- Preserve original timestamps while filling only unassigned legacy documents.
    UPDATE documents SET owner_id=migration_user, updated_at=updated_at WHERE owner_id IS NULL;
    IF EXISTS (SELECT 1 FROM documents d LEFT JOIN users u ON d.owner_id=u.id WHERE u.id IS NULL)
       OR (SELECT COUNT(*) FROM documents)<>original_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Owner validation failed; do not continue';
    END IF;
    ALTER TABLE documents MODIFY COLUMN owner_id BIGINT NOT NULL;
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE()
                   AND TABLE_NAME='documents' AND COLUMN_NAME='owner_id'
                   AND REFERENCED_TABLE_NAME='users' AND REFERENCED_COLUMN_NAME='id') THEN
        ALTER TABLE documents ADD CONSTRAINT fk_documents_owner FOREIGN KEY(owner_id) REFERENCES users(id);
    END IF;
END$$
CALL migrate_m1_m2()$$
DROP PROCEDURE migrate_m1_m2$$
DELIMITER ;
