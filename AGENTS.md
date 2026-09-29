# CodeSpark

AI 零代码应用生成平台，前后端分离。

## 项目结构

- `codeSpark-backend/` — Spring Boot 后端（Java 21），Maven wrapper（`./mvnw`）
- `codeSpark-frontend/` — Vue 3 + TypeScript + Ant Design Vue + Pinia，包管理器 **pnpm**（不要用 npm/yarn）
- `deploy/` — 部署配置
- `docs/` — 项目文档

## 常用命令

```bash
# 后端
cd codeSpark-backend && ./mvnw spring-boot:run   # 服务 http://localhost:8080/api
cd codeSpark-backend && ./mvnw compile -q        # 编译验证

# 前端
cd codeSpark-frontend && pnpm dev                # 开发服务器 http://localhost:5173
cd codeSpark-frontend && pnpm type-check         # vue-tsc 类型检查（提交前必须通过）
cd codeSpark-frontend && pnpm lint               # ESLint

# API 客户端
# 后端接口变更后，在前端目录重新生成 API 客户端
cd codeSpark-frontend && pnpm openapi2ts
```

## 工作约定

- 提交信息遵循 Conventional Commits（`feat` / `fix` / `refactor` / `style` / `perf` / `test` / `docs` / `ci` / `chore`），中文描述
- 前端改动需要通过 `pnpm type-check`；后端改动需要 `./mvnw compile -q` 通过
- 修改后端接口后必须同步执行 `pnpm openapi2ts` 重新生成前端客户端
- Redis 依赖需要内置 RedisJSON 模块（Redis Stack），普通 Redis 不行（详见 README.md）
