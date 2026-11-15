-- Back up ltm_chat, then execute this ENTIRE script in one Workbench connection.
-- Rerunnable after a partially completed migration. No accounts/tables are dropped.
CREATE DATABASE IF NOT EXISTS ltm_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ltm_chat;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_lab1_auth//
CREATE PROCEDURE migrate_lab1_auth()
BEGIN
    DECLARE previous_safe_updates INT DEFAULT 1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET SESSION SQL_SAFE_UPDATES = previous_safe_updates;
        RESIGNAL;
    END;

    SET previous_safe_updates = @@SESSION.SQL_SAFE_UPDATES;
    SET SESSION SQL_SAFE_UPDATES = 0;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='users' AND column_name='email'
    ) THEN
        ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL;
    END IF;

    -- Run even if an earlier attempt already added the column.
    -- Preserve existing emails; fill only missing values.
    UPDATE users
    SET email=CONCAT('legacy-',id,'@lab1.invalid')
    WHERE email IS NULL OR TRIM(email)='';

    ALTER TABLE users MODIFY email VARCHAR(255) NOT NULL;

    -- Detect any existing single-column UNIQUE email index, regardless of its name.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='users' AND non_unique=0
        GROUP BY index_name
        HAVING COUNT(*)=1 AND MAX(column_name)='email'
    ) THEN
        ALTER TABLE users ADD UNIQUE KEY uq_users_email(email);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='users' AND column_name='display_name'
    ) THEN
        ALTER TABLE users ADD COLUMN display_name VARCHAR(100) NULL;
    END IF;

    -- Also repair a column left nullable/unfilled by an interrupted migration.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='users' AND column_name='full_name'
    ) THEN
        SET @migration_sql=
            'UPDATE users SET display_name=COALESCE(NULLIF(TRIM(full_name),''''),username) WHERE display_name IS NULL OR TRIM(display_name)=''''';
        PREPARE migration_statement FROM @migration_sql;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    ELSE
        UPDATE users SET display_name=username
        WHERE display_name IS NULL OR TRIM(display_name)='';
    END IF;

    ALTER TABLE users MODIFY display_name VARCHAR(100) NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='users' AND column_name='updated_at'
    ) THEN
        ALTER TABLE users ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
    END IF;

    ALTER TABLE users
        MODIFY id BIGINT NOT NULL AUTO_INCREMENT,
        MODIFY password VARCHAR(255) NOT NULL;

    -- Restore Workbench's original session setting on success (and via handler on error).
    SET SESSION SQL_SAFE_UPDATES = previous_safe_updates;
END//

CALL migrate_lab1_auth()//
DROP PROCEDURE migrate_lab1_auth//
DELIMITER ;

-- Verify account metadata without displaying passwords.
SELECT id, username, email, display_name, created_at, updated_at
FROM users
ORDER BY id;
