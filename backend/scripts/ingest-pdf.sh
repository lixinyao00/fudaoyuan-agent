#!/bin/bash
# PDF导入脚本
# 用法: ./scripts/ingest-pdf.sh <pdf文件路径>

set -e

PDF_PATH="${1:-./学生手册(1)-1.pdf}"

if [ ! -f "$PDF_PATH" ]; then
    echo "错误: PDF文件不存在: $PDF_PATH"
    exit 1
fi

echo "正在导入PDF: $PDF_PATH"
echo "通过管理API导入..."

# 使用curl调用管理API导入
curl -s -X POST "http://localhost:8080/api/v1/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/admin_cookies.txt

echo ""
echo "正在导入PDF..."
curl -s -X POST "http://localhost:8080/api/v1/admin/ingest" \
  -b /tmp/admin_cookies.txt \
  -F "file=@$PDF_PATH"

echo ""
echo "导入完成！"
