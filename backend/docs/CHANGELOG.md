# CHANGELOG

## v2.0.0 (2026-08-21) - 整改版本

### 新增
- PDF章节识别改进：支持制度标题识别、目录解析、跨页章节继承
- `regulation_name` 字段：知识片段增加制度名称元数据
- 中文检索优化：停用词过滤、关键词归一化、制度标题/条款加权
- 最低相关度阈值拒答：无关查询不再返回低质量结果
- Mock LLM结构化回答：结论→适用条件→制度依据→注意事项
- 风险事件工作流：OPEN→IN_PROGRESS→RESOLVED→CLOSED状态流转
- 管理员风险事件操作：认领、备注、解决、关闭
- 生产安全：会话过期、PDF上传验证、CORS配置、密码安全检查
- MySQL生产脚本：完整初始化脚本、全文索引、新字段迁移
- 微信公众号接口占位：消息接收、被动回复、用户映射、人工转接
- 64条自动化测试（单元/集成/安全/评测/质量）

### 修改
- `PdfIngestionService`: 重写章节识别逻辑，支持多种章节标题格式
- `RetrievalService`: 重写检索逻辑，增加中文NLP处理
- `MockLlmGateway`: 输出结构化政策回答格式
- `SecurityConfig`: 增加会话过期、密码安全检查
- `ChatService`: 使用真实文档信息构建引用
- `AdminController`: 增加风险事件管理接口、PDF上传验证
- `RiskEvent`: 增加状态、处理人、处理备注字段

### 数据库变更
- `knowledge_chunk` 表增加 `regulation_name` 列
- `risk_event` 表增加 `status`、`handler`、`handler_notes`、`resolved_at`、`updated_at` 列
- V003/V004 迁移脚本用于已有数据库升级

### 技术栈
- Java 17, Spring Boot 3.2.5
- MyBatis-Plus 3.5.5
- H2 Database (开发) / MySQL 8.x (生产)
- Apache PDFBox 3.0.2
- Thymeleaf + 原生 CSS/JS
- JUnit 5 + MockMvc

### 已知限制
- 检索为关键词方案，未实现向量检索（EmbeddingGateway待接入）
- MiMo API 仅提供占位实现，需配置真实密钥后启用
- 微信公众号接口未实现，仅预留占位
- 管理员密码当前为明文比对，生产环境建议改为BCrypt哈希
- PDF导入后知识条目默认PENDING，需管理员审核

## v1.0.0 (2026-08-21)

### 新增
- Spring Boot 3.2.5 项目骨架
- H2 内嵌数据库，10张业务表
- Apache PDFBox PDF解析与分段
- 八大知识板块自动分类
- 关键词检索与相关度排序
- Mock LLM 问答服务
- Thymeleaf 问答前端页面
- 引用来源展示与展开
- 用户反馈（有帮助/无帮助/举报）
- 心理危机关键词检测与人工升级
- 管理后台（知识审核、反馈查看、审计日志）
- 管理员登录鉴权
- MiMo API 适配器占位
- 50条自动化测试（单元/集成/安全/评测）
- API 文档与 README
