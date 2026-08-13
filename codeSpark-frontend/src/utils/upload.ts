import request from '@/request'

/**
 * 文件上传公共方法（multipart/form-data）。
 * 放在 src/utils 而非 src/api，避免被 openapi2ts 重新生成时覆盖。
 */

/**
 * 上传用户头像，返回业务响应
 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<API.BaseResponseString>('/file/upload/avatar', {
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 上传应用封面，返回业务响应
 */
export function uploadCover(appId: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('appId', String(appId))
  return request<API.BaseResponseString>('/file/upload/cover', {
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
