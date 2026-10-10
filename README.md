# 辅导员 AI 问答助手

基于《学生手册》等资料的 RAG（检索增强生成）问答系统，包含 Spring Boot 后端和 Vue 3 前端。

本目录是源码发行版，不包含任何生产凭据、数据库数据、Milvus 数据、上传文件、知识库 PDF、编译产物或依赖缓存。

## 技术栈

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Milvus
- Vue 3 + Vue Router 4 + Vite
- DashScope Embedding、MiMo 或 DeepSeek 大模型接口

## 目录结构

```text
src/main/java/                 Spring Boot 后端源码
src/main/resources/            后端配置和数据库 schema
frontend/src/                  Vue 前端源码
frontend/public/               前端公共资源
frontend/package.json          前端依赖和脚本
pom.xml                        Maven 构建配置
docker-compose.yml             MySQL、Milvus 依赖服务模板
.env.example                   环境变量模板
start.sh                       本地启动脚本
stop.sh                        停止后端脚本
```

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm
- Docker 和 Docker Compose
- MySQL 8.0
- Milvus 及其依赖服务

## 配置环境变量

复制环境变量模板：

```bash
cp .env.example .env
```

然后填写自己的配置。不要提交 `.env`，也不要使用他人提供的密钥。

主要变量如下：

| 变量 | 用途 |
| --- | --- |
| `MYSQL_PASSWORD` | 应用连接 MySQL 的密码 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `APP_JWT_SECRET` | JWT 签名密钥，至少 32 字节 |
| `APP_DEFAULT_ADMIN_PASSWORD` | 首次启动时创建的管理员密码 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | Milvus 使用的 MinIO 凭据 |
| `MIMO_API_KEY` | MiMo 模型接口密钥，可选 |
| `DEEPSEEK_API_KEY` | DeepSeek 模型接口密钥，可选 |
| `DASHSCOPE_API_KEY` | Embedding 接口密钥，可选 |

没有配置 Embedding 或向量服务时，系统会使用关键词检索；不要在代码中填入假密钥或真实生产凭据。

## 启动依赖服务

```bash
docker compose --env-file .env up -d mysql etcd minio standalone
```

如果只使用外部 MySQL 或 Milvus，可以根据实际部署情况调整 `docker-compose.yml`。Compose 文件中的密码均通过环境变量读取。

## 构建和启动

### 方式一：使用启动脚本

首次启动或需要重新构建时：

```bash
chmod +x start.sh stop.sh
./start.sh --rebuild --web
```

常用参数：

- `--rebuild`：重新构建后端 JAR
- `--web`：安装前端依赖并重新构建前端
- `--port 8081`：使用其他端口启动后端

启动后访问：

- 前端：http://localhost:8080
- 健康检查：http://localhost:8080/api/health

停止服务：

```bash
./stop.sh
```

### 方式二：分别启动前后端

构建后端：

```bash
mvn clean package -DskipTests
java -jar target/my-rag-agent-1.0.0.jar
```

启动前端开发服务器：

```bash
cd frontend
npm ci
npm run dev
```

Vite 开发服务器默认运行在 http://localhost:5173，并将 `/api` 请求代理到后端 8080 端口。

## 知识库

源码发行版默认关闭内置知识库导入，因为没有携带原始 PDF 文件。配置位于：

```text
src/main/resources/application.yml
```

如果需要使用启动时自动导入功能：

1. 将经过授权的知识库 PDF 放入 `src/main/resources/kb/`。
2. 修改 `rag.kb.enabled` 为 `true`。
3. 根据文件名修改 `rag.kb.title` 和 `rag.kb.resource`。

也可以通过管理端上传文档，具体接口以当前后端实现为准。

## 安全说明

- `.env` 只保存在本地或受控部署环境，不要提交到代码仓库。
- 生产环境必须替换 JWT 密钥、管理员密码、数据库密码和第三方 API Key。
- 不要把 `volumes/`、`uploads/`、`app.log`、`target/` 或数据库备份打包给他人。
- 对外分享源码前，应再次检查提交记录、压缩包和编辑器临时文件中是否包含凭据。
