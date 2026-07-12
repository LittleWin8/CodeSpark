import axios from 'axios'
import { message } from 'ant-design-vue'
import i18n from '@/i18n'
import { getErrorMessage } from '@/utils/errorMessage'

const { t } = i18n.global

const myAxios = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  withCredentials: true,
})

myAxios.interceptors.request.use(
  function (config) {
    return config
  },
  function (error) {
    return Promise.reject(error)
  },
)

myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    if (data.code === 40100) {
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning(t('error.notLogin'))
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    return Promise.reject(error)
  },
)

export default myAxios

/**
 * 处理 API 业务错误，显示本地化错误消息
 * 在组件中调用: apiCall().catch(handleApiError)
 */
export function handleApiError(error: { response?: { data?: { code?: number } }; data?: { code?: number } }) {
  const code = error?.response?.data?.code || error?.data?.code
  if (code) {
    message.error(getErrorMessage(code))
  } else {
    message.error(t('error.default'))
  }
}
