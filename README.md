# CodeSpark

CodeSpark AI 零代码应用生成平台。

## 项目结构

```
.
├── codeSpark-backend/    # Spring Boot 后端（Java 21）
└── codeSpark-frontend/   # Vue 3 前端
```

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
npm install
npm run dev
```

开发服务器默认：`http://localhost:5173`  
前端通过 Vite 代理将 `/api` 转发到后端。

### 生成 API 客户端

后端启动后，在前端目录执行：

```bash
npm run openapi2ts
```
