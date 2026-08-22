# 辅导员专属 AI 问答机器人

基于《2025年学生手册》的智能问答系统，为高校辅导员和学生提供政策查询和基础心理疏导服务。

## 技术栈

- **后端**: Java 17, Spring Boot 3.2.5, MyBatis-Plus 3.5.5
- **数据库**: H2 (开发) / MySQL 8.x (生产)
- **向量数据库**: Milvus 2.3.3 (支持混合检索)
- **文档解析**: Apache PDFBox 3.0.2
- **嵌入模型**: DashScope text-embedding-v4 (1536维向量)
- **大模型API**: DashScope (Qwen系列) / OpenAI兼容API
- **HTTP客户端**: OkHttp 4.x (用于DashScope API调用)
- **前端**: Thymeleaf + 原生 CSS/JS
- **构建**: Maven

## RAG升级特性

### 向量检索
- **Milvus向量数据库**: 支持高性能向量检索
- **DashScope嵌入模型**: 使用text-embedding-v4生成1536维向量
- **混合检索**: 向量检索 + 关键词检索，RRF融合排序

### 智能问答
- **RAG架构**: 检索增强生成，基于学生手册内容生成准确答案
- **多路召回**: 向量检索、关键词检索、HyDE检索
- **流式输出**: SSE协议支持实时流式回答

### 问答流程
```
用户查询 → 问题分类 → 向量检索 → RRF融合 → 上下文构建 → 大模型生成 → 流式输出
```

## 快速启动

### 传统模式 (Mock LLM)
```bash
# 1. 编译项目
mvn clean compile

# 2. 运行测试
mvn clean test

# 3. 启动应用 (Mock模式，无需API密钥)
export ADMIN_PASSWORD=your-secure-password
mvn spring-boot:run

# 4. 访问问答页面
# http://localhost:8080/chat

# 5. 访问管理后台
# http://localhost:8080/admin (用户名和密码通过 ADMIN_USERNAME/ADMIN_PASSWORD 环境变量设置)
```

### RAG模式 (需要Milvus和DashScope)
```bash
# 1. 启动Milvus向量数据库
docker run -d --name milvus -p 19530:19530 milvusdb/milvus:latest

# 2. 设置环境变量
export ADMIN_PASSWORD=your-secure-password
export DASHSCOPE_API_KEY=your-dashscope-api-key
export MILVUS_HOST=localhost
export MILVUS_PORT=19530

# 3. 启动应用
mvn spring-boot:run

# 4. 访问RAG问答接口
# POST http://localhost:8080/api/v1/rag/chat
# GET http://localhost:8080/api/v1/rag/chat/stream?question=奖学金怎么申请
```

## 导入学生手册

```bash
# 通过管理API导入PDF
curl -X POST http://localhost:8080/api/v1/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YOUR_PASSWORD"}' \
  -c cookies.txt

curl -X POST http://localhost:8080/api/v1/admin/ingest \
  -b cookies.txt \
  -F "file=@学生手册(1)-1.pdf"
```

## API 接口

### RAG问答接口 (推荐)
```
POST /api/v1/rag/chat
请求: {"message": "奖学金怎么申请？", "conversation_id": "...", "channel": "web"}
响应: {"answer": "...", "category": "SCHOLARSHIP", "citations": [...], "risk_level": "NONE", ...}
```

### RAG流式问答接口
```
GET /api/v1/rag/chat/stream?question=奖学金怎么申请&conversationId=...
响应: SSE流式事件
  event: retrieval - 检索结果
  event: answer - 逐字答案
  event: citations - 引用信息
  event: complete - 完成事件
```

### 向量检索接口
```
POST /api/v1/rag/search
请求: {"query": "奖学金", "topK": 10}
响应: [{"content": "...", "pageNumber": 10, "source": "学生手册", "score": 0.85}]
```

### 混合检索接口
```
POST /api/v1/rag/search/hybrid
请求: {"query": "奖学金", "topK": 10}
响应: [{"content": "...", "vectorScore": 0.85, "keywordScore": 0.72, "sourceType": "hybrid"}]
```

### 传统问答接口
```
POST /api/v1/chat
请求: {"message": "奖学金怎么申请？", "conversation_id": "...", "channel": "web"}
响应: {"answer": "...", "category": "SCHOLARSHIP", "citations": [...], "risk_level": "NONE", ...}
```

### 传统检索接口
```
POST /api/v1/retrieval/search
请求: {"query": "奖学金", "category": "SCHOLARSHIP", "top_k": 8}
响应: [{"chunk_id": "...", "content": "...", "chapter_title": "...", "page_start": 10, ...}]
```

### 反馈接口
```
POST /api/v1/feedback
请求: {"message_id": "...", "feedback_type": "HELPFUL|NOT_HELPFUL|REPORT", "reason": "..."}
```

### 健康检查
```
GET /api/v1/health
响应: {"status": "UP", "database": "H2-embedded", "knowledgeBase": "active", "aiProvider": "mock"}
```

## 八大知识板块

1. **奖助评优** - 奖学金、助学金、困难认定、勤工助学
2. **考勤请假** - 请假流程、考勤纪律、军训管理
3. **宿舍管理** - 住宿规定、卫生管理、安全规定
4. **学业教务** - 学籍管理、选课、成绩、学位授予
5. **心理健康** - 情绪支持、压力应对、求助渠道
6. **就业毕业** - 毕业条件、就业手续、证书办理
7. **安全应急** - 纪律处分、安全规定、应急处置
8. **日常事务** - 学生证、图书馆、社团、校园服务

## 安全机制

- **风险识别**: 自动检测自伤/他伤/危机关键词，触发人工升级
- **内容审核**: 仅审核通过的知识条目参与正式回答
- **管理鉴权**: 管理接口需登录认证
- **日志审计**: 所有操作记录可追溯
- **敏感脱敏**: 自动脱敏手机号、身份证号等敏感信息

## 配置说明

### 基础配置
- `AI_PROVIDER` - AI提供者 (mock/mimo)
- `MIMO_API_BASE_URL` - MiMo API地址
- `MIMO_API_KEY` - MiMo API密钥
- `RAG_RETRIEVAL_TOP_K` - 检索候选数 (默认8)
- `RAG_EVIDENCE_TOP_N` - 证据数 (默认5)
- `RAG_MIN_SCORE` - 最低相关度阈值 (默认0.15)

### RAG配置
- `MILVUS_HOST` - Milvus服务地址 (默认localhost)
- `MILVUS_PORT` - Milvus服务端口 (默认19530)
- `MILVUS_COLLECTION_NAME` - Milvus集合名称 (默认student_handbook)
- `DASHSCOPE_API_KEY` - DashScope API密钥
- `DASHSCOPE_BASE_URL` - DashScope API地址
- `DASHSCOPE_MODEL` - 大模型名称 (默认qwen-flash)
- `DASHSCOPE_EMBEDDING_MODEL` - 嵌入模型名称 (默认text-embedding-v4)
- `RAG_USE_VECTOR_SEARCH` - 是否使用向量检索 (默认true)
- `RAG_USE_HYBRID_SEARCH` - 是否使用混合检索 (默认true)
- `RAG_SSE_TIMEOUT` - SSE超时时间 (默认30000ms)
- `RAG_SSE_THREAD_POOL_SIZE` - SSE线程池大小 (默认10)

## 项目结构

```
src/main/java/com/example/daoyuanagent/
├── api/          # 控制器、DTO
│   ├── ChatController.java          # 传统问答接口
│   └── RagChatController.java       # RAG问答接口 (新增)
├── config/       # 配置类
│   ├── MilvusConfig.java            # Milvus向量数据库配置 (新增)
│   └── ...
├── domain/       # 实体、枚举
├── service/      # 业务服务
│   ├── ChatService.java             # 传统问答服务
│   ├── RagChatService.java          # RAG问答服务 (新增)
│   ├── VectorSearchService.java     # 向量检索服务 (新增)
│   └── ...
├── provider/     # LLM适配器
├── repository/   # 数据访问
├── util/         # 工具类
└── exception/    # 异常处理
```
