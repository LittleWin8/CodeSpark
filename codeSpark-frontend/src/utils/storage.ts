import request from '@/request'

/**
 * 存储标识 ↔ 访问地址 工具。
 * 放在 src/utils 而非 src/api，避免被 openapi2ts 重新生成时覆盖。
 */

/**
 * 将存储标识解析为可访问 URL（上传成功后预览 / 预签名 URL 过期兜底用）：
 * - oss: → 后端现签预签名 URL
 * - local: → 本地静态访问地址
 *
 * @param storageKey 存储标识（oss: / local: 前缀）
 * @returns 可访问 URL；解析失败返回空字符串
 */
export async function resolveFileUrl(storageKey?: string | null): Promise<string> {
  if (!storageKey) {
    return ''
  }
  try {
    const res = await request<API.BaseResponseString>('/file/resolve', {
      method: 'GET',
      params: { storageKey },
    })
    return res.data?.data ?? ''
  } catch {
    return ''
  }
}

/**
 * 判断字符串是否为存储标识（oss: / local: 前缀）
 */
export function isStorageKey(value?: string | null): boolean {
  return !!value && (value.startsWith('oss:') || value.startsWith('local:'))
}

/**
 * 从预签名 URL 中提取 OSS 对象 key（签名过期 403 时用于重新换票）：
 * https://host/cover/2026/08/19/x.jpg?X-Amz-... → oss:cover/2026/08/19/x.jpg
 * 非 OSS 预签名 URL（本地静态地址 / 外链）返回空串。
 */
export function extractOssKeyFromUrl(url?: string | null): string {
  if (!url || !/^https?:\/\//i.test(url)) {
    return ''
  }
  try {
    const pathname = new URL(url).pathname.replace(/^\//, '') // cover/2026/08/19/x.jpg
    if (!pathname) {
      return ''
    }
    // 已知业务前缀（与 FileBizEnum 对齐），其它路径视为普通外链，不强行换票
    if (!/^(cover|avatar)\//.test(pathname)) {
      return ''
    }
    return `oss:${pathname}`
  } catch {
    return ''
  }
}