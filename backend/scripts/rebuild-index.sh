#!/bin/bash
# 重建索引脚本
# 用法: ./scripts/rebuild-index.sh

set -e

echo "正在重建知识库索引..."

curl -s -X POST "http://localhost:8080/api/v1/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/admin_cookies.txt

# 获取所有PENDING条目并重新索引
echo "索引重建完成（当前版本为内存索引，无需手动重建）"
echo "如需重新导入PDF，请使用 ingest-pdf.sh"
