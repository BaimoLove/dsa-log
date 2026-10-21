-- ============================================================
-- 刷题记录网站 DDL
-- 目标库：dsa_log   字符集：utf8mb4 / utf8mb4_general_ci
-- 说明：problem / problem_list 逻辑删除，其余物理删除；不建外键
-- ============================================================
CREATE DATABASE IF NOT EXISTS `dsa_log`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `dsa_log`;
SET NAMES utf8mb4;

-- 1. 题目（只存题名与外链，不存题干原文）
CREATE TABLE IF NOT EXISTS `problem` (
  `id`             VARCHAR(36)      NOT NULL                   COMMENT '主键 UUID',
  `title`          VARCHAR(255)     NOT NULL                   COMMENT '标题',
  `difficulty`     VARCHAR(8)       NOT NULL DEFAULT 'medium'   COMMENT 'easy/medium/hard',
  `status`         VARCHAR(8)       NOT NULL DEFAULT 'todo'     COMMENT 'todo/solved',
  `approach`       MEDIUMTEXT       NULL                       COMMENT '解题思路/代码（自己写的）',
  `note`           TEXT             NULL                       COMMENT '备注/踩坑',
  `source`         VARCHAR(128)     NULL                       COMMENT '来源',
  `url`            VARCHAR(1024)    NULL                       COMMENT '题目链接（只存外链，不存原文）',
  `solved_at`      DATETIME(3)      NULL                       COMMENT '首次已刷时间（只写一次）',
  `last_solved_at` DATETIME(3)      NULL                       COMMENT '最近已刷时间',
  `solve_count`    INT UNSIGNED     NOT NULL DEFAULT 0          COMMENT '累计已刷次数',
  `review_stage`   TINYINT UNSIGNED NOT NULL DEFAULT 0          COMMENT '复习阶段 0-4',
  `next_review_at` DATETIME(3)      NULL                       COMMENT '下次复习时间，NULL=不在复习池',
  `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`        TINYINT UNSIGNED NOT NULL DEFAULT 0          COMMENT '0 正常 / 1 已删',
  PRIMARY KEY (`id`),
  -- 只保留两条有明确访问路径的索引，其余筛选项交给回表过滤（数据量小，收益为负）
  -- idx_due：待复习队列，deleted 与 status 是等值前缀，next_review_at 是范围列（必须在最后）
  -- idx_updated：列表默认排序，deleted 等值 + updated_at 有序，可直接满足 ORDER BY ... LIMIT
  KEY `idx_due`     (`deleted`,`status`,`next_review_at`),
  KEY `idx_updated` (`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目';

-- 2. 标签（物理删除，名称唯一）
CREATE TABLE IF NOT EXISTS `tag` (
  `id`         VARCHAR(36) NOT NULL,
  `name`       VARCHAR(32) NOT NULL,
  `color`      VARCHAR(16) NOT NULL DEFAULT '#6366f1',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签';

-- 3. 题目-标签
CREATE TABLE IF NOT EXISTS `problem_tag` (
  `problem_id` VARCHAR(36) NOT NULL,
  `tag_id`     VARCHAR(36) NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `idx_tag_problem` (`tag_id`,`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目标签关联';

-- 4. 题单
CREATE TABLE IF NOT EXISTS `problem_list` (
  `id`          VARCHAR(36)  NOT NULL,
  `name`        VARCHAR(64)  NOT NULL,
  `description` VARCHAR(512) NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`     TINYINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_deleted_created` (`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题单';

-- 5. 题单-题目
CREATE TABLE IF NOT EXISTS `problem_list_item` (
  `list_id`    VARCHAR(36) NOT NULL,
  `problem_id` VARCHAR(36) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`list_id`,`problem_id`),
  KEY `idx_problem` (`problem_id`),
  KEY `idx_list_sort` (`list_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题单题目关联';

-- 6. 复习流水（只增不改）
CREATE TABLE IF NOT EXISTS `review_record` (
  `id`             VARCHAR(36) NOT NULL,
  `problem_id`     VARCHAR(36) NOT NULL,
  `kind`           VARCHAR(16) NOT NULL COMMENT 'MARK_SOLVED / MARK_UNSOLVED / REVIEW',
  `rating`         VARCHAR(8)  NULL     COMMENT 'good/hard/again',
  `from_stage`     TINYINT UNSIGNED NULL,
  `to_stage`       TINYINT UNSIGNED NULL,
  `interval_days`  INT UNSIGNED NULL,
  `next_review_at` DATETIME(3) NULL,
  `reviewed_at`    DATETIME(3) NOT NULL COMMENT '服务器时间',
  `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_problem_time` (`problem_id`,`reviewed_at`),
  KEY `idx_time`         (`reviewed_at`),
  KEY `idx_kind_time`    (`kind`,`reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='复习流水';

-- 7. 配置
CREATE TABLE IF NOT EXISTS `app_setting` (
  `setting_key`   VARCHAR(64)   NOT NULL,
  `setting_value` VARCHAR(2048) NOT NULL DEFAULT '',
  `updated_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用配置';

-- 初始化配置
INSERT INTO `app_setting` (`setting_key`,`setting_value`) VALUES
  ('review.dailyLimit','20'), ('review.intervals','[1,3,7,15]'),
  ('ui.pageSize','20'), ('schema.version','1')
ON DUPLICATE KEY UPDATE `setting_value`=VALUES(`setting_value`);
