/**
 * Token 数量格式化工具：统一 k / M 单位（不使用中文单位，便于国际化）
 *
 * 规则：
 * - < 1000         → 原样展示（如 856）
 * - 1000 ~ 100 万  → k（一位小数，去掉 .0，如 12.3k / 123k）
 * - ≥ 100 万       → M（一位小数，去掉 .0，如 1.2M）
 * - 空值 → '-'；-1 视为"不限"哨兵值 → '∞'
 */
export function formatTokens(n?: number | string | null): string {
  // 后端 Long 序列化为字符串（防 JS 精度丢失），先统一转 number，杜绝字典序比较
  const value = Number(n)
  if (n === undefined || n === null || Number.isNaN(value)) {
    return '-'
  }
  if (value < 0) {
    return '∞'
  }
  if (value < 1000) {
    return String(value)
  }
  const trimDotZero = (s: string) => s.replace(/\.0$/, '')
  if (value < 1_000_000) {
    return trimDotZero((value / 1000).toFixed(1)) + 'k'
  }
  return trimDotZero((value / 1_000_000).toFixed(1)) + 'M'
}
