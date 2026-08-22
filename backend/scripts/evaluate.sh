#!/bin/bash
# 离线评测脚本
# 用法: ./scripts/evaluate.sh

set -e

echo "运行项目测试..."
mvn test -q

echo ""
echo "测试完成！查看测试报告: target/surefire-reports/"
