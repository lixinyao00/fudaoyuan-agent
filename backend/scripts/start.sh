#!/bin/bash
# 辅导员AI问答机器人启动脚本
# 用法: ./scripts/start.sh [mock|mimo]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# 加载 .env 文件
if [ -f .env ]; then
    echo "加载 .env 配置..."
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
else
    echo "未找到 .env 文件，使用 .env.example 模板..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "已复制 .env.example → .env，请编辑 .env 填入真实配置"
        echo "重点填写: MIMO_API_KEY"
        exit 1
    fi
fi

# 命令行参数覆盖
PROVIDER="${1:-${AI_PROVIDER:-mock}}"

echo "================================"
echo "  辅导员AI问答机器人"
echo "================================"
echo "  AI Provider: $PROVIDER"
echo "  Database:    ${SPRING_DATASOURCE_URL:-H2 embedded}"
echo "  MiMo Model:  ${MIMO_MODEL:-未配置}"
echo "  端口:        8080"
echo "================================"
echo ""

# 构建
echo "编译项目..."
mvn compile -q

# 启动
echo "启动应用..."
exec mvn spring-boot:run \
    -Dspring-boot.run.arguments="--ai.provider=$PROVIDER \
    --spring.datasource.url=${SPRING_DATASOURCE_URL:-} \
    --spring.datasource.username=${SPRING_DATASOURCE_USERNAME:-} \
    --spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:-} \
    --mimo.api-base-url=${MIMO_API_BASE_URL:-} \
    --mimo.api-key=${MIMO_API_KEY:-} \
    --mimo.model=${MIMO_MODEL:-mimo-v2.5}"
