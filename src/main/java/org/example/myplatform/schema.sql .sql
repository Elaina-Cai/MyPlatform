-- MyPlatform 数据库建表脚本
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci

CREATE DATABASE IF NOT EXISTS `my_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `my_platform`;

-- ==================== 用户表 ====================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号（唯一）',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `nickname` VARCHAR(100) NOT NULL COMMENT '用户昵称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ==================== 好友关系表 ====================
CREATE TABLE IF NOT EXISTS `friend` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `from_user_id` BIGINT NOT NULL COMMENT '申请发起者用户ID',
    `to_user_id` BIGINT NOT NULL COMMENT '申请接收者用户ID',
    `status` TINYINT NOT NULL DEFAULT '0' COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    `message` VARCHAR(255) DEFAULT NULL COMMENT '申请留言',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT '0' COMMENT '软删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_from_user` (`from_user_id`),
    KEY `idx_to_user` (`to_user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

-- ==================== 私聊消息表 ====================
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `sender_id` BIGINT NOT NULL COMMENT '发送者用户ID',
    `user_id` BIGINT NOT NULL COMMENT '收件人用户ID（inbox）',
    `friend_id` BIGINT NOT NULL COMMENT '聊天对象用户ID（用于会话归类）',
    `content` TEXT COMMENT '消息内容',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件URL',
    `file_type` VARCHAR(20) DEFAULT NULL COMMENT '文件类型：image/video',
    `type` TINYINT NOT NULL DEFAULT '0' COMMENT '消息类型：0-普通私聊，1-群邀请',
    `group_id` BIGINT DEFAULT NULL COMMENT '群邀请时关联的群ID',
    `group_name` VARCHAR(100) DEFAULT NULL COMMENT '群邀请时显示的群名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_read` TINYINT NOT NULL DEFAULT '0' COMMENT '已读标记：0-未读，1-已读',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_friend_id` (`friend_id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私聊消息表';

-- ==================== 群聊表 ====================
CREATE TABLE IF NOT EXISTS `chat_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '群聊ID',
    `name` VARCHAR(100) NOT NULL COMMENT '群名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
    `announcement` TEXT COMMENT '群公告',
    `owner_id` BIGINT NOT NULL COMMENT '群主用户ID',
    `join_type` TINYINT NOT NULL DEFAULT '0' COMMENT '加群方式：0-无需审批，1-需要审批',
    `invite_permission` TINYINT NOT NULL DEFAULT '1' COMMENT '邀请权限：0-所有人，1-管理员和群主',
    `allow_member_invite` TINYINT NOT NULL DEFAULT '1' COMMENT '是否允许成员邀请：0-否，1-是',
    `is_muted` TINYINT NOT NULL DEFAULT '0' COMMENT '全员禁言：0-未禁言，1-已禁言',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群聊表';

-- ==================== 群成员表 ====================
CREATE TABLE IF NOT EXISTS `chat_group_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id` BIGINT NOT NULL COMMENT '群聊ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role` TINYINT NOT NULL DEFAULT '0' COMMENT '角色：0-普通成员，1-群主，2-管理员',
    `is_muted` TINYINT NOT NULL DEFAULT '0' COMMENT '是否被禁言：0-否，1-是',
    `mute_expire_time` DATETIME DEFAULT NULL COMMENT '禁言到期时间',
    `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `unread_count` BIGINT DEFAULT '0' COMMENT '未读消息数量',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员表';

-- ==================== 群消息表 ====================
CREATE TABLE IF NOT EXISTS `chat_group_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `group_id` BIGINT NOT NULL COMMENT '群聊ID',
    `sender_id` BIGINT NOT NULL COMMENT '发送者用户ID',
    `content` TEXT COMMENT '消息内容',
    `message_type` TINYINT NOT NULL DEFAULT '1' COMMENT '消息类型：1-文字，2-图片，3-视频',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件URL',
    `file_type` VARCHAR(20) DEFAULT NULL COMMENT '文件类型',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_group_id` (`group_id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群消息表';

-- ==================== 群消息已读表 ====================
CREATE TABLE IF NOT EXISTS `chat_group_message_read` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id` BIGINT NOT NULL COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_user` (`message_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群消息已读表';

-- ==================== 入群申请表 ====================
CREATE TABLE IF NOT EXISTS `chat_group_join_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id` BIGINT NOT NULL COMMENT '群聊ID',
    `applicant_id` BIGINT NOT NULL COMMENT '申请人用户ID',
    `inviter_id` BIGINT DEFAULT NULL COMMENT '邀请人用户ID（主动邀请时）',
    `status` TINYINT NOT NULL DEFAULT '0' COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    `type` TINYINT NOT NULL DEFAULT '0' COMMENT '类型：0-主动申请，1-邀请入群',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
    `handled_by` BIGINT DEFAULT NULL COMMENT '处理人用户ID',
    PRIMARY KEY (`id`),
    KEY `idx_group_id` (`group_id`),
    KEY `idx_applicant_id` (`applicant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入群申请表';
