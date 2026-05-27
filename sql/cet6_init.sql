-- CET-6 词库 + 刷题进度表
CREATE TABLE IF NOT EXISTS cet6_word (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    pos VARCHAR(20) NOT NULL COMMENT '词性',
    meaning VARCHAR(500) NOT NULL COMMENT '中文释义',
    UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quiz_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cet_word_id BIGINT NOT NULL,
    consecutive_correct INT DEFAULT 0 COMMENT '连续答对次数 0/1/2/3',
    total_wrong INT DEFAULT 0 COMMENT '累计答错次数',
    last_quiz_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近出题时间',
    UNIQUE KEY uk_user_word (user_id, cet_word_id),
    INDEX idx_user_mastered (user_id, consecutive_correct),
    INDEX idx_user_wrong (user_id, consecutive_correct, last_quiz_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quiz_daily_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quiz_date DATE NOT NULL,
    total_questions INT DEFAULT 0,
    correct_answers INT DEFAULT 0,
    last_quiz_at DATETIME DEFAULT NULL COMMENT '最近一次答题时间',
    UNIQUE KEY uk_user_date (user_id, quiz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- v2 migration: add last_quiz_at column for existing databases.
SET @has_quiz_daily_last_quiz_at := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quiz_daily_stats'
      AND COLUMN_NAME = 'last_quiz_at'
);
SET @add_quiz_daily_last_quiz_at := IF(
    @has_quiz_daily_last_quiz_at = 0,
    'ALTER TABLE quiz_daily_stats ADD COLUMN last_quiz_at DATETIME DEFAULT NULL COMMENT ''recent quiz answer time''',
    'SELECT 1'
);
PREPARE stmt FROM @add_quiz_daily_last_quiz_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE quiz_daily_stats
SET last_quiz_at = TIMESTAMP(quiz_date)
WHERE last_quiz_at IS NULL;

CREATE TABLE IF NOT EXISTS quiz_leaderboard_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    like_date DATE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_from_to_date (from_user_id, to_user_id, like_date),
    INDEX idx_to_user_date (to_user_id, like_date),
    INDEX idx_from_user_date (from_user_id, like_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PK duel tables
CREATE TABLE IF NOT EXISTS pk_user_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points INT DEFAULT 0,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    draws INT DEFAULT 0,
    matches INT DEFAULT 0,
    highest_streak INT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pk_daily_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    base_points_gained INT DEFAULT 0,
    streak_points_gained INT DEFAULT 0,
    net_points INT DEFAULT 0,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    draws INT DEFAULT 0,
    matches INT DEFAULT 0,
    highest_streak INT DEFAULT 0,
    UNIQUE KEY uk_user_date (user_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pk_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_a_id BIGINT NOT NULL,
    player_b_id BIGINT NOT NULL,
    winner_user_id BIGINT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'playing',
    questions_total INT DEFAULT 20,
    questions_completed INT DEFAULT 0,
    player_a_score INT DEFAULT 0,
    player_b_score INT DEFAULT 0,
    player_a_points_delta INT DEFAULT 0,
    player_b_points_delta INT DEFAULT 0,
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME DEFAULT NULL,
    INDEX idx_player_a (player_a_id),
    INDEX idx_player_b (player_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pk_match_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    question_index INT NOT NULL,
    cet_word_id BIGINT NOT NULL,
    buzz_user_id BIGINT DEFAULT NULL,
    answer_user_id BIGINT DEFAULT NULL,
    chosen VARCHAR(500) DEFAULT NULL,
    correct BOOLEAN DEFAULT NULL,
    score_delta INT DEFAULT 0,
    answered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_match_id (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pk_match_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    question_index INT NOT NULL,
    cet_word_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    options_json TEXT DEFAULT NULL,
    UNIQUE KEY uk_match_question (match_id, question_index),
    INDEX idx_match_id (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pk_user_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    invisible BOOLEAN DEFAULT FALSE,
    first_win_declaration VARCHAR(15) DEFAULT '',
    revenge_declaration VARCHAR(15) DEFAULT '',
    sweep_declaration VARCHAR(15) DEFAULT '',
    final_win_declaration VARCHAR(15) DEFAULT '',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has_pk_first_win_declaration := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pk_user_setting'
      AND COLUMN_NAME = 'first_win_declaration'
);
SET @add_pk_declarations := IF(
    @has_pk_first_win_declaration = 0,
    'ALTER TABLE pk_user_setting ADD COLUMN first_win_declaration VARCHAR(15) DEFAULT '''', ADD COLUMN revenge_declaration VARCHAR(15) DEFAULT '''', ADD COLUMN sweep_declaration VARCHAR(15) DEFAULT '''', ADD COLUMN final_win_declaration VARCHAR(15) DEFAULT ''''',
    'SELECT 1'
);
PREPARE stmt FROM @add_pk_declarations;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS pk_pair_daily_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    reward_date DATE NOT NULL,
    rewarded_matches INT DEFAULT 0,
    UNIQUE KEY uk_pair_date (user_low_id, user_high_id, reward_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
