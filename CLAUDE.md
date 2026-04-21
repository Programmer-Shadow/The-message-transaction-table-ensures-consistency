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

### 消费端幂等流程（IdempotentRecord）

```
ShipmentConsumer.onMessage(MessageExt)
    ↓
提取 messageId（来自生产端 message.getKeys()，全链路唯一）
    ↓
IdempotentService.executeIdempotent(messageId, businessLogic)   [@Transactional]
    │
    ├── 1. INSERT idempotent_record (status=PROCESSING)
    │       │
    │       ├── 成功                          → 继续 3
    │       └── DuplicateKeyException（UNIQUE message_id 冲突）
    │             │
    │             └── 2. SELECT 已有记录
    │                   ├── status=SUCCESS    → return（幂等跳过，不重复发货）
    │                   └── status=PROCESSING → 上次崩溃，继续 3
    │
    ├── 3. businessLogic.run()
    │       └── ShipmentService.createShipment [Propagation.MANDATORY 加入当前事务]
    │
    └── 4. UPDATE idempotent_record
            ├── 业务成功 → status=SUCCESS
            └── 业务异常 → status=FAILED + 事务回滚 + 抛异常 → MQ 重新投递
```

**核心保证**：
- **并发互斥**：UNIQUE KEY(`message_id`) + INSERT 行锁，同一条消息的并发消费被串行化
- **原子性**：幂等记录与 `shipment` 在同一事务提交，保证"要么都成功、要么都回滚"（不存在幂等记录已写但业务失败的中间态）
- **崩溃恢复**：`PROCESSING` 状态说明上一轮执行中途死掉，允许本轮重新处理；`SUCCESS` 才算真正终态
- **消息 ID 来源**：生产端 `OutboxScheduler` 把 `outbox_message.id` 作为 RocketMQ 的 `KEYS`，消费端通过 `message.getKeys()` 拿到

**`Runnable businessLogic` 设计**：
`IdempotentService.executeIdempotent(messageId, Runnable)` 接收一个 `Runnable`，调用方（`ShipmentConsumer`）传入 Lambda：
```java
idempotentService.executeIdempotent(messageId, () ->
    shipmentService.createShipment(orderNo, productId, quantity)
);
```
`businessLogic.run()` 最终执行 `ShipmentService.createShipment()` → `INSERT INTO shipment`。
用 `Runnable` 的好处：`IdempotentService` 不依赖任何业务类，任何业务逻辑传进来都能复用同一套幂等保护。

### 关键设计决策

1. **Outbox 调度器多实例安全**：用 CAS（`UPDATE ... WHERE status=PENDING AND version=?`）避免重复发送
2. **消费端幂等**：`idempotent_record` 表的 UNIQUE 约束 + INSERT 行锁实现并发去重，业务与幂等记录同事务保证原子性
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
