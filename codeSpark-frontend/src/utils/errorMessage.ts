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
