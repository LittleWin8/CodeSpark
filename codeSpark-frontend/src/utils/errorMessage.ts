import i18n from '@/i18n'

const { t, te } = i18n.global

/**
 * 后端错误码 → i18n 翻译 key 的映射
 * key 与 ErrorCode 枚举的 code 值一一对应
 */
const errorCodeMap: Record<number, string> = {
  40000: 'error.paramsError',
  40100: 'error.notLogin',
  40101: 'error.noAuth',
  40300: 'error.forbidden',
  40400: 'error.notFound',
  50000: 'error.systemError',
  50001: 'error.operationError',
}

/**
 * 后端自定义错误 message（稳定英文 key）→ i18n key
 */
const errorMessageMap: Record<string, string> = {
  INVALID_APP_ID: 'error.invalidAppId',
  EMPTY_CHAT_MESSAGE: 'error.emptyChatMessage',
  EMPTY_INIT_PROMPT: 'error.emptyInitPrompt',
  PAGE_SIZE_LIMIT: 'error.pageSizeLimit',
  INVALID_PAGE_SIZE: 'error.invalidPageSize',
  APP_NOT_FOUND: 'error.appNotFound',
  INVALID_CODE_GEN_TYPE: 'error.invalidCodeGenType',
  APP_CODE_NOT_GENERATED: 'error.appCodeNotGenerated',
  APP_DEPLOY_FAILED: 'error.appDeployFailed',
  APP_DEPLOY_UPDATE_FAILED: 'error.appDeployUpdateFailed',
  APP_DELETE_FAILED: 'error.appDeleteFailed',
  EMPTY_PARAMS: 'error.emptyParams',
  ACCOUNT_TOO_SHORT: 'error.accountTooShort',
  PASSWORD_TOO_SHORT: 'error.passwordTooShort',
  PASSWORD_MISMATCH: 'error.passwordMismatch',
  ACCOUNT_EXISTS: 'error.accountExists',
  EMAIL_EXISTS: 'error.emailExists',
  INVALID_EMAIL: 'error.invalidEmail',
  REGISTER_FAILED: 'error.registerFailed',
  EMPTY_ACCOUNT_OR_PASSWORD: 'error.emptyAccountOrPassword',
  INVALID_ACCOUNT: 'error.invalidAccount',
  INVALID_PASSWORD: 'error.invalidPassword',
  LOGIN_FAILED: 'error.loginFailed',
  NOT_LOGIN: 'error.notLogin',
  EMPTY_FILE: 'error.emptyFile',
  FILE_TOO_LARGE: 'error.fileTooLarge',
  FILE_SAVE_FAILED: 'error.fileSaveFailed',
  FILE_DOWNLOAD_FAILED: 'error.fileDownloadFailed',
  INVALID_FILE_TYPE: 'error.invalidFileType',
  INVALID_FILE_URL: 'error.invalidFileUrl',
  FORBIDDEN_FILE_URL: 'error.forbiddenFileUrl',
  UNSUPPORTED_EXTERNAL_URL: 'error.externalUrlNotSupported',
  INVALID_STORAGE_REQUEST: 'error.invalidStorageRequest',
  CANNOT_DELETE_SELF: 'error.cannotDeleteSelf',
  INVALID_MESSAGE_TYPE: 'error.invalidMessageType',
  INVALID_USER_ID: 'error.invalidUserId',
  CODE_GEN_TYPE_REQUIRED: 'error.codeGenTypeRequired',
  UNSUPPORTED_CODE_GEN_TYPE: 'error.unsupportedCodeGenType',
  ONLY_NAMING_ALLOWED: 'error.onlyNamingAllowed',
  APP_ID_REQUIRED: 'error.appIdRequired',
  EMPTY_FILE_URL: 'error.emptyFileUrl',
  SCREENSHOT_GENERATE_FAILED: 'error.screenshotGenerateFailed',
  CHROME_INIT_FAILED: 'error.chromeInitFailed',
  IMAGE_SAVE_FAILED: 'error.imageSaveFailed',
  IMAGE_COMPRESS_FAILED: 'error.imageCompressFailed',
  CODE_CONTENT_EMPTY: 'error.codeContentEmpty',
  CODE_RESULT_EMPTY: 'error.codeResultEmpty',
  UPDATE_COVER_FAILED: 'error.updateCoverFailed',
  AVATAR_UPDATE_LIMIT_EXCEEDED: 'error.avatarUpdateLimitExceeded',
  VUE_BUILD_FAILED: 'error.vueBuildFailed',
  VUE_DIST_NOT_FOUND: 'error.vueDistNotFound',
  ok: 'error.default',
}

/**
 * 根据后端返回的错误码 / message 获取本地化错误消息
 */
export function getErrorMessage(code?: number, message?: string): string {
  if (message) {
    const mappedKey = errorMessageMap[message]
    if (mappedKey) {
      return t(mappedKey)
    }
    // 若后端直接返回了 i18n key
    if (te(message)) {
      return t(message)
    }
  }
  if (code !== undefined && errorCodeMap[code]) {
    return t(errorCodeMap[code])
  }
  return t('error.default')
}
