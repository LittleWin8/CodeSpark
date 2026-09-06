# CodeSpark

CodeSpark AI 零代码应用生成平台。

## 项目结构

```
.
├── codeSpark-backend/    # Spring Boot 后端（Java 21）
└── codeSpark-frontend/   # Vue 3 前端
```

## 环境要求

后端通过 langchain4j-community-redis 将对话记忆持久化到 Redis，依赖 Redis 服务端的 **RedisJSON** 模块（`JSON.GET` / `JSON.SET` 命令）。普通 Redis（各发行版 apt 包、官方 `redis` docker 镜像等）默认不包含该模块，运行时会出现：

```
redis.clients.jedis.exceptions.JedisDataException: ERR unknown command 'JSON.GET'
```

因此需要提供内置 RedisJSON 的 Redis（如 Redis Stack）或额外加载 rejson 模块的实例，而非裸 Redis。

## 本地开发

### 后端

```bash
cd codeSpark-backend
./mvnw spring-boot:run
```

服务地址：`http://localhost:8080/api`  
API 文档：`http://localhost:8080/api/doc.html`

### 前端

```bash
cd codeSpark-frontend
pnpm install
pnpm dev
```

开发服务器默认：`http://localhost:5173`  
前端通过 Vite 代理将 `/api` 转发到后端。

### 生成 API 客户端

后端启动后，在前端目录执行：

```bash
pnpm openapi2ts
```
