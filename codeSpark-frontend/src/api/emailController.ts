/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /email/code */
export async function sendEmailCode(body: API.EmailCodeRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/email/code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /email/resetPassword */
export async function resetPasswordByEmail(
  body: API.ResetPasswordByEmailRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/email/resetPassword', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
