-- Additive migration after owner_id and content_format migrations. Never grants old documents.
USE dbclasshw;
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
