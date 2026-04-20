# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

电商 Outbox Pattern 演示项目，包含两个独立 Spring Boot 微服务：
- **order-service** (port 8080)：订单创建 + Outbox 消息发布
- **warehouse-service** (port 8081)：RocketMQ 消费 + 幂等发货

## Common Commands

### 启动基础设施
```bash
docker compose up -d    # 启动 MySQL + RocketMQ
```

### 构建
```bash
cd order-service && mvn clean package
cd warehouse-service && mvn clean package
```

### 运行服务
```bash
cd order-service && mvn spring-boot:run
cd warehouse-service && mvn spring-boot:run
```

### 测试 API
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2,"orderNo":"ORDER-001"}'
```

## Architecture

### 核心流程（Outbox Pattern）

```
HTTP Request
    ↓
OrderService（单事务）
    ├── 扣减 inventory 库存
    ├── 写入 orders 表
    └── 写入 outbox_message 表（status=PENDING）
         ↓
OutboxScheduler（每15秒轮询）
    └── CAS 更新状态后推送 RocketMQ topic: order-created
              ↓
    WarehouseConsumer（RocketMQ 消费）
        ├── 查重 idempotent_record（UNIQUE message_id）
        └── 写入 shipment 表
```

### 关键设计决策

1. **Outbox 调度器多实例安全**：用 CAS（`UPDATE ... WHERE status=PENDING AND version=?`）避免重复发送
2. **消费端幂等**：`idempotent_record` 表的 UNIQUE 约束，insert ignore 实现去重
3. **重试策略**：`outbox_message.next_retry_at` + 指数退避，联合索引 `(status, next_retry_at)`
4. **事件载荷完整性**：outbox 存完整 JSON payload，不依赖事后查询

### 数据库

| 服务 | 数据库 | 核心表 |
|------|--------|--------|
| order-service | `order_db` | `orders`, `inventory`, `outbox_message` |
| warehouse-service | `warehouse_db` | `shipment`, `idempotent_record` |

初始化 SQL 见 `init-db.sql`，docker compose 会自动执行。

### 配置文件
- `order-service/src/main/resources/application.yml`
- `warehouse-service/src/main/resources/application.yml`
