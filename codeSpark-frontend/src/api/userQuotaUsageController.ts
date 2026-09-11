/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /quota/admin/reset */
export async function adminReset(body: API.ResetQuotaRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/quota/admin/reset', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /quota/admin/reset/all */
export async function adminResetAll(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/quota/admin/reset/all', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /quota/admin/usage/page */
export async function adminPageUsage(
  body: API.QuotaUsageQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAdminQuotaUsageVO>('/quota/admin/usage/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /quota/me */
export async function getMyQuota(options?: { [key: string]: any }) {
  return request<API.BaseResponseQuotaInfoVO>('/quota/me', {
    method: 'GET',
    ...(options || {}),
  })
}
