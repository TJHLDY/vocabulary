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
    UNIQUE KEY uk_user_date (user_id, quiz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
