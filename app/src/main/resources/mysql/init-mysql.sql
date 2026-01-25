-- EmailWebhookBridge MySQL 初始化脚本
-- 建议使用的数据库编码：utf8mb4

CREATE DATABASE IF NOT EXISTS `email_webhook_bridge` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `email_webhook_bridge`;

-- 1. Webhook 配置表
CREATE TABLE IF NOT EXISTS `webhook_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) DEFAULT NULL COMMENT 'Webhook 备注名称',
    `url` VARCHAR(255) NOT NULL UNIQUE COMMENT '目标 URL',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用 (1:启用, 0:禁用)',
    `last_status` VARCHAR(255) DEFAULT 'UNKNOWN' COMMENT '健康状态: UP, DOWN, UNKNOWN',
    `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '最后一次错误信息',
    `last_tested_at` DATETIME DEFAULT NULL COMMENT '最后测试时间',
    `created_at` DATETIME DEFAULT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 邮件处理记录表 (SCA 原始记录)
CREATE TABLE IF NOT EXISTS `sca_process_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `sca_project_name` VARCHAR(255) DEFAULT NULL,
    `sca_application_name` VARCHAR(255) DEFAULT NULL,
    `sca_branch` VARCHAR(255) DEFAULT NULL,
    `sca_task_id` VARCHAR(255) DEFAULT NULL,
    `sca_app_id` VARCHAR(255) DEFAULT NULL,
    `sca_start_time` VARCHAR(255) DEFAULT NULL,
    `sca_end_time` VARCHAR(255) DEFAULT NULL,
    `received_at` DATETIME DEFAULT NULL COMMENT '邮件接收时间',
    `is_allowed` TINYINT(1) DEFAULT NULL COMMENT '是否通过规则过滤',
    `sca_raw_content` TEXT DEFAULT NULL COMMENT '原始邮件内容'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Webhook 推送日志表
CREATE TABLE IF NOT EXISTS `webhook_push_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `sca_project_name` VARCHAR(255) DEFAULT NULL,
    `sca_application_name` VARCHAR(255) DEFAULT NULL,
    `sca_branch` VARCHAR(255) DEFAULT NULL,
    `sca_task_id` VARCHAR(255) DEFAULT NULL,
    `sca_app_id` VARCHAR(255) DEFAULT NULL,
    `sca_start_time` VARCHAR(255) DEFAULT NULL,
    `sca_end_time` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(255) DEFAULT NULL COMMENT '推送状态: SUCCESS / FAILED',
    `http_status_code` INT DEFAULT NULL COMMENT 'HTTP 响应码',
    `response_body` VARCHAR(2048) DEFAULT NULL COMMENT '响应内容副本',
    `webhook_url` VARCHAR(255) DEFAULT NULL COMMENT '推送目标 URL',
    `webhook_name` VARCHAR(255) DEFAULT NULL COMMENT '推送目标备注',
    `pushed_at` DATETIME DEFAULT NULL COMMENT '推送时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 索引记录
-- Webhook 配置：URL 是唯一的，已有唯一索引

-- 邮件处理记录：
-- 1. 根据任务 ID 查询（用于精确定位）
CREATE INDEX idx_process_record_task_id ON sca_process_record(sca_task_id);
-- 2. 根据时间和项目名排序/过滤（常用查询）
CREATE INDEX idx_process_record_received_at ON sca_process_record(received_at);
CREATE INDEX idx_process_record_project ON sca_process_record(sca_project_name);

-- Webhook 推送日志：
-- 1. Webhook 详情页核心索引：指定 URL 下的状态过滤和统计
CREATE INDEX idx_push_log_url_status ON webhook_push_log(webhook_url, status);
-- 2. Webhook 详情页趋势图索引：指定 URL 下的时间范围查询
CREATE INDEX idx_push_log_url_pushed_at ON webhook_push_log(webhook_url, pushed_at);
-- 3. 全局日志查询：根据任务 ID 或项目名过滤
CREATE INDEX idx_push_log_task_id ON webhook_push_log(sca_task_id);
CREATE INDEX idx_push_log_project ON webhook_push_log(sca_project_name);
