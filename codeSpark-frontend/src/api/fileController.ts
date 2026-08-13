/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /file/avatar/${param0}/${param1} */
export async function getAvatar(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAvatarParams,
  options?: { [key: string]: any }
) {
  const { userId: param0, fileName: param1, ...queryParams } = params
  return request<string>(`/file/avatar/${param0}/${param1}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /file/cover/${param0}/${param1} */
export async function getCover(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getCoverParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, fileName: param1, ...queryParams } = params
  return request<string>(`/file/cover/${param0}/${param1}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /file/upload/avatar */
export async function uploadAvatar(body: {}, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/file/upload/avatar', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /file/upload/cover */
export async function uploadCover(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadCoverParams,
  body: {},
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/file/upload/cover', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: {
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}
