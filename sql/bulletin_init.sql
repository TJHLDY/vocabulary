CREATE TABLE IF NOT EXISTS bulletin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user` ADD COLUMN seen_bulletin_id BIGINT DEFAULT NULL;

-- 插入第一条公告（示例）
INSERT INTO bulletin (content) VALUES ('欢迎使用 VocabNote！🎉\n\n新功能上线：\n• 六级刷题 - CET-6 词汇选择题\n• 本周概览图表 - 学习数据可视化\n• 留言板 - 反馈问题或建议');
