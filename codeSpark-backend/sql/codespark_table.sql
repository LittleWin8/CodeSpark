-- 数据库初始化
-- 1. 连接到 postgres 库执行：CREATE DATABASE codespark;
-- 2. 连接到 codespark 库后执行以下脚本
CREATE DATABASE codespark;

-- 1. 用户表（user 为 PostgreSQL 保留字，需加双引号）
CREATE TABLE IF NOT EXISTS "user"
(
    id              BIGSERIAL PRIMARY KEY,
    "userAccount"   VARCHAR(256)  NOT NULL,
    "userEmail"     VARCHAR(256),
    "userPassword"  VARCHAR(512)  NOT NULL,
    "userName"      VARCHAR(256),
    "userAvatar"    VARCHAR(1024),
    "userProfile"   VARCHAR(512),
    "userRole"      VARCHAR(256)  NOT NULL DEFAULT 'user',
    "editTime"      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createTime"    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updateTime"    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isDelete"      SMALLINT      NOT NULL DEFAULT 0,
    "vipExpireTime" TIMESTAMP,
    "vipCode"       VARCHAR(128),
    "vipNumber"     BIGINT,
    "shareCode"     VARCHAR(20),
    "inviteUser"    BIGINT,
    CONSTRAINT uk_userAccount UNIQUE ("userAccount"),
    CONSTRAINT uk_userEmail UNIQUE ("userEmail"),
    CONSTRAINT uk_shareCode UNIQUE ("shareCode")
);

CREATE INDEX IF NOT EXISTS idx_userName ON "user" ("userName");
-- 头像存储标识查询优化（定时任务 like 'oss:%' / 'local:%'）
CREATE INDEX IF NOT EXISTS idx_user_avatar ON "user" ("userAvatar");
CREATE INDEX IF NOT EXISTS idx_user_avatar_oss ON "user" ("userAvatar") WHERE "userAvatar" LIKE 'oss:%';
CREATE INDEX IF NOT EXISTS idx_user_avatar_local ON "user" ("userAvatar") WHERE "userAvatar" LIKE 'local:%';

COMMENT ON TABLE "user" IS '用户表';
COMMENT ON COLUMN "user".id IS 'id';
COMMENT ON COLUMN "user"."userAccount" IS '账号';
COMMENT ON COLUMN "user"."userEmail" IS '邮箱';
COMMENT ON COLUMN "user"."userPassword" IS '密码';
COMMENT ON COLUMN "user"."userName" IS '用户昵称';
COMMENT ON COLUMN "user"."userAvatar" IS '用户头像';
COMMENT ON COLUMN "user"."userProfile" IS '用户简介';
COMMENT ON COLUMN "user"."userRole" IS '用户角色：user/admin';
COMMENT ON COLUMN "user"."editTime" IS '编辑时间';
COMMENT ON COLUMN "user"."createTime" IS '创建时间';
COMMENT ON COLUMN "user"."updateTime" IS '更新时间';
COMMENT ON COLUMN "user"."isDelete" IS '是否删除';
COMMENT ON COLUMN "user"."vipExpireTime" IS '会员过期时间';
COMMENT ON COLUMN "user"."vipCode" IS '会员兑换码';
COMMENT ON COLUMN "user"."vipNumber" IS '会员编号';
COMMENT ON COLUMN "user"."shareCode" IS '分享码';
COMMENT ON COLUMN "user"."inviteUser" IS '邀请用户 id';

-- 自动更新 updateTime（替代 MySQL 的 ON UPDATE CURRENT_TIMESTAMP）
CREATE OR REPLACE FUNCTION update_user_update_time()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW."updateTime" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_user_update_time ON "user";
CREATE TRIGGER trigger_user_update_time
    BEFORE UPDATE
    ON "user"
    FOR EACH ROW
    EXECUTE PROCEDURE update_user_update_time();

-- 2. 应用表（app，对应 AI 应用：名称/封面/初始化 prompt/代码生成类型/部署标识等）
CREATE TABLE IF NOT EXISTS app
(
    id            BIGSERIAL PRIMARY KEY,
    "appName"     VARCHAR(256),
    "cover"       VARCHAR(512),
    "initPrompt"  TEXT,
    "codeGenType" VARCHAR(64),
    "deployKey"   VARCHAR(64),
    "deployedTime" TIMESTAMP,
    "priority"    INTEGER     NOT NULL DEFAULT 0,
    "userId"      BIGINT      NOT NULL,
    "editTime"    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createTime"  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updateTime"  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isDelete"    SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_deployKey UNIQUE ("deployKey") -- 确保部署标识唯一
);

-- 提升基于应用名称的查询性能
CREATE INDEX IF NOT EXISTS idx_appName ON app ("appName");
-- 提升基于用户 ID 的查询性能
CREATE INDEX IF NOT EXISTS idx_userId ON app ("userId");
-- 封面存储标识查询优化（定时任务 like 'oss:%' / 'local:%' 全量扫用）
CREATE INDEX IF NOT EXISTS idx_app_cover ON app ("cover");
CREATE INDEX IF NOT EXISTS idx_app_cover_oss ON app ("cover") WHERE "cover" LIKE 'oss:%';
CREATE INDEX IF NOT EXISTS idx_app_cover_local ON app ("cover") WHERE "cover" LIKE 'local:%';

COMMENT ON TABLE app IS '应用表';
COMMENT ON COLUMN app.id IS 'id';
COMMENT ON COLUMN app."appName" IS '应用名称';
COMMENT ON COLUMN app."cover" IS '应用封面';
COMMENT ON COLUMN app."initPrompt" IS '应用初始化的 prompt';
COMMENT ON COLUMN app."codeGenType" IS '代码生成类型（枚举）';
COMMENT ON COLUMN app."deployKey" IS '部署标识';
COMMENT ON COLUMN app."deployedTime" IS '部署时间';
COMMENT ON COLUMN app."priority" IS '优先级';
COMMENT ON COLUMN app."userId" IS '创建用户id';
COMMENT ON COLUMN app."editTime" IS '编辑时间';
COMMENT ON COLUMN app."createTime" IS '创建时间';
COMMENT ON COLUMN app."updateTime" IS '更新时间';
COMMENT ON COLUMN app."isDelete" IS '是否删除';

-- 自动更新 updateTime（替代 MySQL 的 ON UPDATE CURRENT_TIMESTAMP）
CREATE OR REPLACE FUNCTION update_app_update_time()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW."updateTime" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_app_update_time ON app;
CREATE TRIGGER trigger_app_update_time
    BEFORE UPDATE
    ON app
    FOR EACH ROW
    EXECUTE PROCEDURE update_app_update_time();

-- 3. 对话历史表（chat_history）
CREATE TABLE IF NOT EXISTS chat_history
(
    id           BIGSERIAL PRIMARY KEY,
    "message"    TEXT         NOT NULL,
    "messageType" VARCHAR(32) NOT NULL,
    "appId"      BIGINT       NOT NULL,
    "userId"     BIGINT       NOT NULL,
    "createTime" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updateTime" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isDelete"   SMALLINT     NOT NULL DEFAULT 0
);

-- 提升查询性能的索引
-- 提升基于应用的查询性能
CREATE INDEX IF NOT EXISTS idx_chat_appId ON chat_history ("appId");
-- 提升基于时间的查询性能
CREATE INDEX IF NOT EXISTS idx_chat_createTime ON chat_history ("createTime");
-- 游标查询核心索引
CREATE INDEX IF NOT EXISTS idx_chat_appId_createTime ON chat_history ("appId", "createTime");

COMMENT ON TABLE chat_history IS '对话历史';
COMMENT ON COLUMN chat_history.id IS 'id';
COMMENT ON COLUMN chat_history."message" IS '消息';
COMMENT ON COLUMN chat_history."messageType" IS '消息类型：user/ai';
COMMENT ON COLUMN chat_history."appId" IS '应用id';
COMMENT ON COLUMN chat_history."userId" IS '创建用户id';
COMMENT ON COLUMN chat_history."createTime" IS '创建时间';
COMMENT ON COLUMN chat_history."updateTime" IS '更新时间';
COMMENT ON COLUMN chat_history."isDelete" IS '是否删除';

-- 自动更新 updateTime（替代 MySQL 的 ON UPDATE CURRENT_TIMESTAMP）
CREATE OR REPLACE FUNCTION update_chat_history_update_time()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW."updateTime" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_chat_history_update_time ON chat_history;
CREATE TRIGGER trigger_chat_history_update_time
    BEFORE UPDATE
    ON chat_history
    FOR EACH ROW
    EXECUTE PROCEDURE update_chat_history_update_time();
