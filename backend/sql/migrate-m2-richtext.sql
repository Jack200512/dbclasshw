-- After M1-to-M2 ownership migration. Stop backend writers first.
-- Additive only; no existing content, owner, version or timestamp is rewritten.
USE dbclasshw;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='documents' AND COLUMN_NAME='content_format');
SET @ddl = IF(@exists=0, 'ALTER TABLE documents ADD COLUMN content_format VARCHAR(16) NOT NULL DEFAULT ''PLAIN_TEXT'' AFTER content', 'SELECT 1');
PREPARE richtext_migration FROM @ddl;
EXECUTE richtext_migration;
DEALLOCATE PREPARE richtext_migration;
