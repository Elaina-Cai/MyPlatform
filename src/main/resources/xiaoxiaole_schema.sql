CREATE TABLE IF NOT EXISTS `xiaoxiaole_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `current_level` INT DEFAULT 1 COMMENT '当前关卡',
    `highest_level` INT DEFAULT 1 COMMENT '历史最高关卡',
    `total_score` BIGINT DEFAULT 0 COMMENT '总分数',
    `best_score` BIGINT DEFAULT 0 COMMENT '最高分',
    `play_count` INT DEFAULT 0 COMMENT '游玩次数',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_highest_level` (`highest_level`),
    CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消消乐游戏记录';

-- 创建排行榜视图（可选）
CREATE VIEW IF NOT EXISTS `xiaoxiaole_rank_view` AS
SELECT 
    @row := @row + 1 AS rank,
    r.*
FROM (
    SELECT * FROM xiaoxiaole_record ORDER BY highest_level DESC, best_score DESC
) r, (SELECT @row := 0) t;