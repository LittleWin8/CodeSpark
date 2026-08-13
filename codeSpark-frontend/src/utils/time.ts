import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'

dayjs.extend(relativeTime)

/**
 * 格式化为相对时间（如「5 小时前」）
 */
export function formatRelativeTime(time?: string, locale = 'zh-CN'): string {
  if (!time) {
    return ''
  }
  const dayjsLocale = locale.startsWith('zh') ? 'zh-cn' : 'en'
  return dayjs(time).locale(dayjsLocale).fromNow()
}

/**
 * 格式化为绝对时间
 */
export function formatDateTime(time?: string): string {
  if (!time) {
    return ''
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
