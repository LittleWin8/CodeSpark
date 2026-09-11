/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /userTokenUsage/me/totalTokens */
export async function getTotalTokens(options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/userTokenUsage/me/totalTokens', {
    method: 'GET',
    ...(options || {}),
  })
}
