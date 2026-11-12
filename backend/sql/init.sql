-- Run explicitly with an authorized MySQL account. Never drops or truncates data.
CREATE DATABASE IF NOT EXISTS dbclasshw
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dbclasshw;

-- M1 transition schema. owner_id and user relationships will be added in M2.
-- IF NOT EXISTS preserves existing tables; it does not migrate their structure.
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
