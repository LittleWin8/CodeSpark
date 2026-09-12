package top.littlewin.codespark.exception;

import lombok.Getter;

/**
 * 业务错误信息统一枚举：集中管理后端返回给前端的「稳定英文 key」。
 * <p>
 * 约定：
 * <ul>
 *   <li>业务层抛错一律引用本枚举，禁止硬编码裸字符串/中文（避免前后端 key 不一致、无法按语言切换）；</li>
 *   <li>{@link #getKey()} 返回枚举名（大写下划线），即为:
 *       {@code BusinessException(ErrorCode, ErrorMessage)} 组装出、经
 *       {@code GlobalExceptionHandler} 返回给前端的 message 字段；</li>
 *   <li>前端 {@code errorMessageMap} 与 i18n 资源需与每个 key 一一对应。</li>
 * </ul>
 */
@Getter
public enum ErrorMessage {

    // ---------- 通用 / 参数 ----------
    EMPTY_PARAMS, // 参数不能为空
    PAGE_SIZE_LIMIT, // 每页最多查询 20 个应用
    INVALID_PAGE_SIZE, // 每页数量必须在 1-50 之间

    // ---------- 应用 ----------
    INVALID_APP_ID, // 应用 ID 不合法
    APP_NOT_FOUND, // 应用不存在
    EMPTY_CHAT_MESSAGE, // 对话内容不能为空
    EMPTY_INIT_PROMPT, // 请输入创建提示词

    // ---------- 代码生成类型 ----------
    INVALID_CODE_GEN_TYPE, // 应用代码生成类型无效
    CODE_GEN_TYPE_REQUIRED, // 生成类型不能为空
    UNSUPPORTED_CODE_GEN_TYPE, // 不支持的代码生成类型
    APP_CODE_NOT_GENERATED, // 请先生成应用后再部署
    APP_DEPLOY_FAILED, // 应用部署失败
    APP_DEPLOY_UPDATE_FAILED, // 更新部署信息失败
    APP_DELETE_FAILED, // 删除应用失败
    UPDATE_COVER_FAILED, // 更新应用封面失败
    VUE_BUILD_FAILED, // Vue 项目构建失败，请重试
    VUE_DIST_NOT_FOUND, // Vue 构建成功但未生成 dist 目录

    // ---------- 用户 / 认证 ----------
    ACCOUNT_TOO_SHORT, // 账号长度不能少于 4 位
    PASSWORD_TOO_SHORT, // 密码长度不能少于 8 位
    PASSWORD_MISMATCH, // 两次输入的密码不一致
    INVALID_EMAIL, // 邮箱格式不正确
    EMAIL_EXISTS, // 邮箱已注册
    ACCOUNT_EXISTS, // 账号已存在
    REGISTER_FAILED, // 注册失败，请稍后重试
    EMPTY_ACCOUNT_OR_PASSWORD, // 账号和密码不能为空
    INVALID_ACCOUNT, // 账号格式不正确
    INVALID_PASSWORD, // 密码格式不正确
    LOGIN_FAILED, // 用户不存在或密码错误
    NOT_LOGIN, // 请先登录
    CANNOT_DELETE_SELF, // 不能删除自己

    // ---------- 聊天 ----------
    INVALID_MESSAGE_TYPE, // 消息类型不合法
    INVALID_USER_ID, // 用户 ID 不合法

    // ---------- 文件 / 存储 ----------
    EMPTY_FILE, // 请选择要上传的文件
    FILE_TOO_LARGE, // 文件大小不能超过 5MB
    INVALID_FILE_TYPE, // 不支持的文件类型
    FILE_SAVE_FAILED, // 文件保存失败
    INVALID_FILE_URL, // 图片地址不合法
    UNSUPPORTED_EXTERNAL_URL, // 暂不支持外链图片，请上传本地图片
    INVALID_STORAGE_REQUEST, // 存储请求参数不合法
    AVATAR_UPDATE_LIMIT_EXCEEDED, // 今日头像更新次数已达上限，每日最多5次

    // ---------- 截图 / 网页截图 ----------
    EMPTY_FILE_URL, // 图片地址不能为空
    APP_ID_REQUIRED, // 应用 ID 不能为空
    SCREENSHOT_GENERATE_FAILED, // 生成网页截图失败
    CHROME_INIT_FAILED, // 初始化 Chrome 浏览器失败
    IMAGE_SAVE_FAILED, // 保存图片失败
    IMAGE_COMPRESS_FAILED, // 压缩图片失败

    // ---------- 代码文件保存 ----------
    CODE_CONTENT_EMPTY, // HTML 代码内容不能为空
    CODE_RESULT_EMPTY, // 代码结果对象不能为空

    // ---------- 项目下载 ----------
    DOWNLOAD_NO_AUTH, // 无权限下载该应用代码
    APP_CODE_NOT_FOUND, // 应用代码不存在，请先生成代码
    PROJECT_PATH_EMPTY, // 项目路径不能为空
    DOWNLOAD_FILENAME_EMPTY, // 下载文件名不能为空
    PROJECT_DIR_NOT_FOUND, // 项目目录不存在
    PROJECT_PATH_NOT_DIR, // 指定路径不是目录
    PROJECT_DOWNLOAD_FAILED, // 项目打包下载失败

    // ---------- 用户额度 ----------
    QUOTA_EXCEEDED, // 本月额度已用完，请下月再试

    // ---------- 预置示例应用 ----------
    PRESET_VARIANT_EMPTY, // 预置变体未配置文件列表
    PRESET_RESOURCE_MISSING, // 预置产物文件缺失

    // ---------- AI 生成约束 ----------
    OUTPUT_TRUNCATED, // 输出过长被截断，请拆分需求后重试
    GENERATION_TOO_MANY_STEPS; // 生成步骤过多，请把需求拆小后重试

    /**
     * @return 稳定英文 key（即枚举名）
     */
    public String getKey() {
        return name();
    }
}