/* eslint-disable */
import request from '@/request'

/** 发送邮箱验证码 POST /email/code */
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

/** 通过邮箱验证码重置密码 POST /email/resetPassword */
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
