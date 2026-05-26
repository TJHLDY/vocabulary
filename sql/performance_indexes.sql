-- Optional production indexes for large wordbooks and admin user lists.
-- Safe to run more than once on MySQL.

SET @has_idx_word_user_count := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'word'
      AND INDEX_NAME = 'idx_word_user_count'
);
SET @add_idx_word_user_count := IF(
    @has_idx_word_user_count = 0,
    'CREATE INDEX idx_word_user_count ON word(user_id, count, id)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_word_user_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_word_user_created := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'word'
      AND INDEX_NAME = 'idx_word_user_created'
);
SET @add_idx_word_user_created := IF(
    @has_idx_word_user_created = 0,
    'CREATE INDEX idx_word_user_created ON word(user_id, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_word_user_created;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_user_created_at := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND INDEX_NAME = 'idx_user_created_at'
);
SET @add_idx_user_created_at := IF(
    @has_idx_user_created_at = 0,
    'CREATE INDEX idx_user_created_at ON user(created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_user_created_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_user_invited_by := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND INDEX_NAME = 'idx_user_invited_by'
);
SET @add_idx_user_invited_by := IF(
    @has_idx_user_invited_by = 0,
    'CREATE INDEX idx_user_invited_by ON user(invited_by)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_user_invited_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
