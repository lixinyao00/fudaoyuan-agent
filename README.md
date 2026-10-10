# 辅导员智能问答系统

给辅导员和学生用的问答机器人，基于《学生手册》做的检索增强生成（RAG）。后端是 Spring Boot，前端用的 Vue 3。

## 用到的技术

后端：
- Java 17 + Spring Boot 3.2.5
- MyBatis-Plus 3.5.5 做数据访问
- MySQL 8.0 存业务数据
- Milvus 做向量检索
- PDFBox 解析 PDF 文档
- OkHttp 调用大模型 API

前端：
- Vue 3 + Vite
- Vue Router 4
- Pinia 状态管理

大模型接口支持：
- 阿里 DashScope 做文本向量化
- 小米 MiMo / DeepSeek 做对话生成

## 项目结构

```
backend/                    后端代码
  src/main/java/            Java 源码
  src/main/resources/       配置文件、数据库脚本
  pom.xml                   Maven 依赖配置
  start.sh                  启动脚本
  stop.sh                   停止脚本

frontend/                   前端代码
  src/                      Vue 源码
  public/                   静态资源
  package.json              npm 依赖

docker-compose.yml          依赖服务编排（MySQL、Milvus、MinIO、etcd）
Dockerfile                  应用容器构建
.env.example                环境变量示例
```

## 环境准备

需要提前装好：
- JDK 17 或更高版本
- Maven 3.8+
- Node.js 18+
- Docker 和 Docker Compose（跑 MySQL、Milvus 用）

## 配置

先把环境变量模板复制一份，然后填上自己的配置：

```bash
cp .env.example .env
```

主要配置项：

| 变量名 | 说明 |
| --- | --- |
| MYSQL_PASSWORD | 数据库密码 |
| MYSQL_ROOT_PASSWORD | MySQL root 密码 |
| APP_JWT_SECRET | JWT 签名密钥，至少 32 字节 |
| APP_DEFAULT_ADMIN_PASSWORD | 管理员初始密码 |
| MINIO_ACCESS_KEY / MINIO_SECRET_KEY | MinIO 存储密钥（Milvus 用） |
| MIMO_API_KEY | MiMo 接口密钥（可选） |
| DEEPSEEK_API_KEY | DeepSeek 接口密钥（可选） |
| DASHSCOPE_API_KEY | 向量化接口密钥（可选） |

如果没有配置向量服务，系统会自动降级到关键词检索，也能用。

## 启动

### 先启动依赖服务

```bash
docker compose --env-file .env up -d mysql etcd minio standalone
```

### 方式一：用脚本一键启动

```bash
chmod +x start.sh stop.sh
./start.sh --rebuild --web
```

参数说明：
- `--rebuild` 重新构建后端
- `--web` 重新构建前端
- `--port 8081` 换个端口启动

启动后：
- 前端地址：http://localhost:8080
- 健康检查：http://localhost:8080/api/health

停止服务：
```bash
./stop.sh
```

### 方式二：手动分别启动

后端：
```bash
mvn clean package -DskipTests
java -jar target/my-rag-agent-1.0.0.jar
```

前端开发模式：
```bash
cd frontend
npm ci
npm run dev
```

前端开发服务器默认在 http://localhost:5173，会把 `/api` 请求代理到后端 8080 端口。

## 知识库

默认没有开启启动时自动导入知识库，因为源码里没带 PDF 文件。

如果要用这个功能：
1. 把 PDF 文件放到 `backend/src/main/resources/kb/` 目录
2. 改 `application.yml` 里的 `rag.kb.enabled` 为 `true`
3. 根据实际情况改 `rag.kb.title` 和 `rag.kb.resource`

也可以在管理后台上传文档。

## 几点提醒

- `.env` 文件不要提交到 git，里面都是敏感信息
- 部署到生产环境记得改掉默认的 JWT 密钥、管理员密码这些
- `volumes/`、`uploads/`、`app.log` 这些目录不要打包给别人
