-- 数据库初始化
-- 1. 连接到 postgres 库执行：CREATE DATABASE codespark;
-- 2. 连接到 codespark 库后执行以下脚本
CREATE DATABASE codespark;

-- 1. 用户表（user 为 PostgreSQL 保留字，需加双引号）
CREATE TABLE IF NOT EXISTS "user"
(
    id              BIGSERIAL PRIMARY KEY,
    "userAccount"   VARCHAR(256)  NOT NULL,
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
    CONSTRAINT uk_userAccount UNIQUE ("userAccount")
);

CREATE INDEX IF NOT EXISTS idx_userName ON "user" ("userName");

COMMENT ON TABLE "user" IS '用户表';
COMMENT ON COLUMN "user".id IS 'id';
COMMENT ON COLUMN "user"."userAccount" IS '账号';
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
