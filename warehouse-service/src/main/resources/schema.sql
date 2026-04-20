-- warehouse_db 建表脚本

CREATE TABLE IF NOT EXISTS `shipment` (
    `id`         BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `order_no`   VARCHAR(64) NOT NULL,
    `product_id` BIGINT      NOT NULL,
    `quantity`   INT         NOT NULL,
    `status`     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_order_no` (`order_no`)   -- 防止重复发货（幂等的最后一道防线）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 幂等记录表：消费端防重复消费的核心
-- message_id UNIQUE KEY 保证：同一消息无论消费多少次，业务只执行一次
-- =====================================================================
CREATE TABLE IF NOT EXISTS `idempotent_record` (
    `id`             BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `message_id`     VARCHAR(64) NOT NULL,          -- 对应 outbox_message.message_id
    `consumer_group` VARCHAR(64) NOT NULL,
    `status`         VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING/SUCCESS/FAILED
    `created_at`     DATETIME(3) NOT NULL,
    `updated_at`     DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
