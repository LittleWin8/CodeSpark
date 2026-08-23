/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /file/${param0}/${param1}/${param2}/${param3}/${param4} */
export async function getFile(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getFileParams,
  options?: { [key: string]: any }
) {
  const {
    biz: param0,
    yyyy: param1,
    MM: param2,
    dd: param3,
    fileName: param4,
    ...queryParams
  } = params
  return request<string>(`/file/${param0}/${param1}/${param2}/${param3}/${param4}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /file/resolve */
export async function resolve(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resolveParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/file/resolve', {
    method: 'GET',
    params: {
      ...params,
    },
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
