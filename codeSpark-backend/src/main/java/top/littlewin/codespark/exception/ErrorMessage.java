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
    EMPTY_PARAMS,
    PAGE_SIZE_LIMIT,
    INVALID_PAGE_SIZE,

    // ---------- 应用 ----------
    INVALID_APP_ID,
    APP_NOT_FOUND,
    EMPTY_CHAT_MESSAGE,
    EMPTY_INIT_PROMPT,

    // ---------- 代码生成类型 ----------
    INVALID_CODE_GEN_TYPE,
    CODE_GEN_TYPE_REQUIRED,
    UNSUPPORTED_CODE_GEN_TYPE,
    APP_CODE_NOT_GENERATED,
    APP_DEPLOY_FAILED,
    APP_DEPLOY_UPDATE_FAILED,
    APP_DELETE_FAILED,
    UPDATE_COVER_FAILED,
    VUE_BUILD_FAILED,
    VUE_DIST_NOT_FOUND,

    // ---------- 用户 / 认证 ----------
    ACCOUNT_TOO_SHORT,
    PASSWORD_TOO_SHORT,
    PASSWORD_MISMATCH,
    INVALID_EMAIL,
    EMAIL_EXISTS,
    ACCOUNT_EXISTS,
    REGISTER_FAILED,
    EMPTY_ACCOUNT_OR_PASSWORD,
    INVALID_ACCOUNT,
    INVALID_PASSWORD,
    LOGIN_FAILED,
    NOT_LOGIN,
    CANNOT_DELETE_SELF,

    // ---------- 聊天 ----------
    INVALID_MESSAGE_TYPE,
    INVALID_USER_ID,

    // ---------- 文件 / 存储 ----------
    EMPTY_FILE,
    FILE_TOO_LARGE,
    INVALID_FILE_TYPE,
    FILE_SAVE_FAILED,
    INVALID_FILE_URL,
    UNSUPPORTED_EXTERNAL_URL,
    INVALID_STORAGE_REQUEST,
    AVATAR_UPDATE_LIMIT_EXCEEDED,

    // ---------- 截图 / 网页截图 ----------
    EMPTY_FILE_URL,
    APP_ID_REQUIRED,
    SCREENSHOT_GENERATE_FAILED,
    CHROME_INIT_FAILED,
    IMAGE_SAVE_FAILED,
    IMAGE_COMPRESS_FAILED,

    // ---------- 代码文件保存 ----------
    CODE_CONTENT_EMPTY,
    CODE_RESULT_EMPTY,

    // ---------- 项目下载 ----------
    DOWNLOAD_NO_AUTH,
    APP_CODE_NOT_FOUND,
    PROJECT_PATH_EMPTY,
    DOWNLOAD_FILENAME_EMPTY,
    PROJECT_DIR_NOT_FOUND,
    PROJECT_PATH_NOT_DIR,
    PROJECT_DOWNLOAD_FAILED;

    /**
     * @return 稳定英文 key（即枚举名）
     */
    public String getKey() {
        return name();
    }
}