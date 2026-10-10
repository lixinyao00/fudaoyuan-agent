#!/bin/bash
# 启动脚本
# 用法: ./start.sh [--rebuild] [--web] [--port PORT]
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$BASE_DIR"

REBUILD=0
BUILD_WEB=0
PORT=8080

while [[ $# -gt 0 ]]; do
    case $1 in
        --rebuild) REBUILD=1 ;;
        --web)     BUILD_WEB=1 ;;
        --port)    PORT="$2"; shift ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
    shift
done

echo "=== 停止旧进程 ==="
pkill -f 'my-rag-agent-1.0.0.jar' 2>/dev/null || true
# 等待进程退出 + H2 文件锁释放
for i in $(seq 1 10); do
    pgrep -f 'my-rag-agent-1.0.0.jar' >/dev/null 2>&1 || break
    sleep 1
done
sleep 1

echo "=== 编译 ==="
# 先构建前端（产物进 src/main/resources/static），再打 jar，保证静态资源最新
if [ $BUILD_WEB -eq 1 ]; then
    (cd frontend && npm ci && npm run build)
fi
if [ $REBUILD -eq 1 ] || [ ! -f target/my-rag-agent-1.0.0.jar ]; then
    mvn -q package -DskipTests
fi

echo "=== 启动 ==="
nohup java -Dserver.port=$PORT -jar target/my-rag-agent-1.0.0.jar > app.log 2>&1 </dev/null &
disown

# 等待服务就绪（/api/health 为开放接口；KB 自动导入在启动日志中查看）
echo "=== 等待服务就绪（最多 90 秒） ==="
for i in $(seq 1 30); do
    if curl -s -m 2 "http://localhost:$PORT/api/health" 2>/dev/null | grep -q 'UP'; then
        echo "✅ 服务就绪"
        break
    fi
    sleep 3
    echo -n "."
done
echo

echo "=== 服务地址 ==="
echo "http://localhost:$PORT"
echo "健康检查: http://localhost:$PORT/api/health"

echo "=== 最近日志 ==="
tail -5 app.log