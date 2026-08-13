/**
 * 应用 URL 工具
 * 统一拼接部署地址与生成预览地址，域名来自环境变量
 */

/** 应用部署域名（部署后对外访问的地址，nginx 服务地址） */
const DEPLOY_BASE_URL = import.meta.env.VITE_APP_DEPLOY_BASE_URL || 'http://localhost'

/** 应用生成预览基础路径（预览 AI 生成的应用，走后端静态资源接口） */
const PREVIEW_BASE_URL = import.meta.env.VITE_APP_PREVIEW_BASE_URL || '/api/static'

/**
 * 获取部署访问地址
 *
 * @param deployKey 部署标识
 * @returns 完整部署 URL，如 http://localhost/{deployKey}/
 */
export function getDeployUrl(deployKey?: string | null): string {
  if (!deployKey) {
    return ''
  }
  return `${DEPLOY_BASE_URL}/${deployKey}/`
}

/**
 * 获取生成预览地址
 *
 * @param codeGenType 代码生成类型（html / multi_file）
 * @param appId 应用 ID
 * @returns 预览 URL，如 /api/static/{codeGenType}_{appId}/
 */
export function getPreviewUrl(codeGenType?: string | null, appId?: number | string | null): string {
  if (!codeGenType || !appId) {
    return ''
  }
  return `${PREVIEW_BASE_URL}/${codeGenType}_${appId}/`
}
