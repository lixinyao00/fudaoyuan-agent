#!/bin/bash
# 启动脚本：MySQL + MiMo 模式
cd /home/lixinyao/javaweb/daoyuan-agent

# 从 .env 加载所有变量
set -a
source .env
set +a

# 设置 MySQL 驱动
export SPRING_DATASOURCE_DRIVER=com.mysql.cj.jdbc.Driver

echo "=== 启动配置 ==="
echo "Provider: $AI_PROVIDER"
echo "Model: $MIMO_MODEL"
echo "DB: $SPRING_DATASOURCE_URL"
echo "================"

mvn spring-boot:run -q
