#!/bin/bash
# 停止脚本
pkill -f 'my-rag-agent-1.0.0.jar' 2>/dev/null && echo "✅ 停止成功" || echo "⚠ 未发现运行进程"
# 等待 H2 锁释放
sleep 1
rm -f data/*.lock 2>/dev/null || true