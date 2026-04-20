-- order_db 建表脚本，Spring Boot 启动时自动执行

CREATE TABLE IF NOT EXISTS `orders` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `order_no`   VARCHAR(64)  NOT NULL,
    `user_id`    BIGINT       NOT NULL,
    `product_id` BIGINT       NOT NULL,
    `quantity`   INT          NOT NULL,
    `amount`     DECIMAL(12,2) NOT NULL,
    `status`     VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    `created_at` DATETIME(3)  NOT NULL,
    `updated_at` DATETIME(3)  NOT NULL,
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `inventory` (
    `id`              BIGINT  PRIMARY KEY AUTO_INCREMENT,
    `product_id`      BIGINT  NOT NULL,
    `total_stock`     INT     NOT NULL,
    `locked_stock`    INT     NOT NULL DEFAULT 0,
    `available_stock` INT     NOT NULL,
    `version`         INT     NOT NULL DEFAULT 0,
    `updated_at`      DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Outbox 核心表：记录"待发送的消息意图"
-- 与业务表在同一库，保证同一 DB 事务内写入
-- =====================================================================
CREATE TABLE IF NOT EXISTS `outbox_message` (
    `id`             BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `message_id`     VARCHAR(64)  NOT NULL,          -- 全局唯一，消费端用于幂等
    `aggregate_type` VARCHAR(64)  NOT NULL,          -- 聚合根类型，如 ORDER
    `aggregate_id`   VARCHAR(64)  NOT NULL,          -- 聚合根ID，如 order_no
    `topic`          VARCHAR(128) NOT NULL,          -- MQ Topic
    `tag`            VARCHAR(64),
    `payload`        JSON         NOT NULL,          -- 完整事件快照（解耦，不存引用）
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/PROCESSING/SENT/FAILED
    `retry_count`    INT          NOT NULL DEFAULT 0,
    `max_retry`      INT          NOT NULL DEFAULT 5,
    `next_retry_at`  DATETIME(3)  NOT NULL,          -- 指数退避控制下次重试时间
    `sent_at`        DATETIME(3),
    `error_message`  VARCHAR(512),
    `created_at`     DATETIME(3)  NOT NULL,
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status_retry` (`status`, `next_retry_at`)  -- Scheduler 查询核心索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
