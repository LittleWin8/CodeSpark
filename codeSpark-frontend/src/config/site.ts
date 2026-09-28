/**
 * 站点信息配置
 * 个人信息（站点地址、站长、备案、联系方式）均通过 VITE_ 环境变量注入，
 * 仓库中不保留任何个人信息；变量留空时页面对应内容不展示。
 * 本地开发可在 .env.development 中覆盖，线上由 CI 变量注入。
 */

const env = import.meta.env

/** 站点名称（默认使用开源项目名） */
export const siteName = env.VITE_SITE_NAME || 'CodeSpark AI'

/** 站点访问地址（页脚站点链接；留空表示与当前页面同源） */
export const siteUrl = env.VITE_SITE_URL || ''

/** 站长名称与主页链接（留空则页脚不展示站长入口） */
export const webmasterName = env.VITE_WEBMASTER_NAME || ''
export const webmasterUrl = env.VITE_WEBMASTER_URL || ''

/** 联系邮箱（留空则协议页面不展示邮箱，改为通用联系说明） */
export const contactEmail = env.VITE_CONTACT_EMAIL || ''

/** ICP 备案号（留空则页脚不展示） */
export const icpNumber = env.VITE_ICP_NUMBER || ''

/** ICP 备案查询链接（工业和信息化部备案系统） */
export const icpUrl = env.VITE_ICP_URL || 'https://beian.miit.gov.cn/'

/** 公安备案号（留空则页脚不展示；备案码从备案号中的数字段自动提取） */
export const gaBeianNumber = env.VITE_GA_BEIAN_NUMBER || ''
export const gaBeianCode = gaBeianNumber.replace(/\D/g, '')

/** 公安备案标识图片（官方警徽素材不进开源仓库，通过变量指定自托管地址；留空则仅展示文字） */
export const gaBeianIcon = env.VITE_GA_BEIAN_ICON || ''

/** 公安备案查询链接（默认根据备案码生成查询地址，也可通过变量指定） */
export const gaBeianUrl =
  env.VITE_GA_BEIAN_URL || `https://beian.mps.gov.cn/#/query/webSearch?code=${gaBeianCode}`
