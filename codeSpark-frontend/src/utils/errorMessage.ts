import i18n from '@/i18n'

const { t } = i18n.global

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
 * 根据后端返回的错误码获取本地化错误消息
 * @param code 后端错误码
 * @returns 本地化的错误消息，找不到映射时返回默认消息
 */
export function getErrorMessage(code: number): string {
  const key = errorCodeMap[code]
  return key ? t(key) : t('error.default')
}
