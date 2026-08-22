# API 文档

## 基础信息
- 基础路径: `/api/v1`
- 内容类型: `application/json`
- 字符编码: UTF-8
- 检索方案: 基于关键词匹配（含中文停用词过滤、问句归一化、加权排序），后续计划接入向量检索（EmbeddingGateway）

## 问答接口

### POST /api/v1/chat
发送消息获取回答

**请求体:**
```json
{
  "conversation_id": "可选，续接会话",
  "message": "必填，用户消息",
  "user_id": "可选，用户标识",
  "channel": "web"
}
```

**响应体:**
```json
{
  "answer": "AI回答",
  "category": "SCHOLARSHIP",
  "related_categories": ["DAILY"],
  "risk_level": "NONE",
  "citations": [{
    "chunk_id": "abc123",
    "document_name": "学生手册(1)-1.pdf",
    "chapter_title": "清华大学学生奖学金管理规定 - 第一章 总则",
    "page_start": 106,
    "page_end": 107,
    "article_ref": "第十二条",
    "content_snippet": "...",
    "score": 0.85
  }],
  "needs_human_review": false,
  "confidence": 0.85,
  "conversation_id": "uuid",
  "request_id": "uuid",
  "prompt_version": "v1.0",
  "fallback": false
}
```

## 检索接口

### POST /api/v1/retrieval/search
直接检索知识库

**当前实现**: 基于关键词匹配，包含中文停用词过滤、问句词归一化、制度标题加权、条款匹配加权、分类匹配加权。最低相关度阈值 0.15。

**请求体:**
```json
{
  "query": "奖学金",
  "category": "SCHOLARSHIP",
  "top_k": 8
}
```

## 反馈接口

### POST /api/v1/feedback
提交用户反馈

**请求体:**
```json
{
  "message_id": "消息ID",
  "feedback_type": "HELPFUL|NOT_HELPFUL|REPORT",
  "reason": "可选，原因"
}
```

## 健康检查

### GET /api/v1/health
返回系统状态（不泄露敏感信息）

## 管理接口（需登录）

### POST /api/v1/admin/login
管理员登录

### POST /api/v1/admin/logout
退出登录

### POST /api/v1/admin/ingest
导入PDF文件（需登录，仅支持PDF，最大50MB）

### GET /api/v1/admin/chunks
查询知识条目

### POST /api/v1/admin/chunks/{id}/review
审核知识条目

### GET /api/v1/admin/feedback
查询用户反馈

### GET /api/v1/admin/risks
查询风险事件

**参数:** `status` (可选) - 筛选状态: OPEN, IN_PROGRESS, RESOLVED, CLOSED

### GET /api/v1/admin/risks/{id}
查询风险事件详情

### POST /api/v1/admin/risks/{id}/claim
认领风险事件（状态变为IN_PROGRESS）

### POST /api/v1/admin/risks/{id}/note
为风险事件添加备注

**请求体:** `{"note": "备注内容"}`

### POST /api/v1/admin/risks/{id}/resolve
解决风险事件（状态变为RESOLVED）

**请求体:** `{"note": "解决说明"}` (可选)

### POST /api/v1/admin/risks/{id}/close
关闭风险事件（状态变为CLOSED）

**请求体:** `{"note": "关闭说明"}` (可选)

### GET /api/v1/admin/audit
查询审计日志

### GET /api/v1/admin/documents
查询已导入文档

## 微信公众号接口（未完成正式上线）

### GET /api/v1/wechat/callback
微信服务器验证接口（占位）

### POST /api/v1/wechat/callback
微信消息接收接口（占位）

### POST /api/v1/wechat/transfer
人工转接接口（占位）

### GET /api/v1/wechat/user-mapping
用户映射查询（占位）
