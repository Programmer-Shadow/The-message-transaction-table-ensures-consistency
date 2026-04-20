#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ORDER_LOG=/tmp/order-service.log
WAREHOUSE_LOG=/tmp/warehouse-service.log

echo "=== 检查前置条件 ==="

# 检查 MySQL
if ! bash -c 'echo > /dev/tcp/localhost/3306' 2>/dev/null; then
  echo "❌ MySQL 未启动，请先在 Windows 启动 MySQL"
  exit 1
fi
echo "✅ MySQL 已就绪"

# 检查 RocketMQ NameServer
if ! bash -c 'echo > /dev/tcp/localhost/9876' 2>/dev/null; then
  echo "❌ RocketMQ NameServer 未启动，请先在 Windows 执行："
  echo "   mqnamesrv.cmd"
  echo "   mqbroker.cmd -n localhost:9876 -c ../conf/broker.conf autoCreateTopicEnable=true"
  exit 1
fi
echo "✅ RocketMQ 已就绪"

echo ""
echo "=== 停止旧进程 ==="
kill $(lsof -ti:8080,8081) 2>/dev/null && echo "✅ 旧进程已停止" || echo "（无旧进程）"
sleep 1

echo ""
echo "=== 启动服务 ==="
nohup java -jar "$PROJECT_DIR/order-service/target/"*.jar > "$ORDER_LOG" 2>&1 &
echo "✅ order-service 已启动 (PID: $!, log: $ORDER_LOG)"

nohup java -jar "$PROJECT_DIR/warehouse-service/target/"*.jar > "$WAREHOUSE_LOG" 2>&1 &
echo "✅ warehouse-service 已启动 (PID: $!, log: $WAREHOUSE_LOG)"

echo ""
echo "=== 等待服务就绪 ==="
printf "等待 order-service(8080)..."
until bash -c 'echo > /dev/tcp/localhost/8080' 2>/dev/null; do printf "."; sleep 2; done
echo " ✅"

printf "等待 warehouse-service(8081)..."
until bash -c 'echo > /dev/tcp/localhost/8081' 2>/dev/null; do printf "."; sleep 2; done
echo " ✅"

echo ""
echo "=== 启动完成 ==="
echo "  创建订单: POST http://localhost:8080/orders"
echo "  查看发货: GET  http://localhost:8081/shipments"
echo "  查看outbox: GET http://localhost:8080/demo/outbox"
echo ""
echo "测试命令："
echo "  curl -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \\"
echo "    -d '{\"userId\":1,\"productId\":1001,\"quantity\":1,\"orderNo\":\"ORDER-001\"}'"
