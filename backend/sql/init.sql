-- Fresh installations only. Existing M1 databases must use migrate-m1-m2.sql.
CREATE DATABASE IF NOT EXISTS dbclasshw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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
INSERT INTO users(username,email,password_hash,enabled)
SELECT '__m1_migration__','m1-migration@invalid.local','!disabled:m1-migration',FALSE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='__m1_migration__');
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    content_format VARCHAR(16) NOT NULL DEFAULT 'PLAIN_TEXT',
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(6) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_permissions_document_user UNIQUE (document_id,user_id),
    INDEX idx_permissions_user (user_id),
    CONSTRAINT ck_permissions_role CHECK (role IN ('EDITOR','VIEWER')),
    CONSTRAINT fk_permissions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

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
