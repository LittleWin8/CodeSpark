/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  /** 站点名称 */
  readonly VITE_SITE_NAME?: string
  /** 站点访问地址 */
  readonly VITE_SITE_URL?: string
  /** 站长名称与主页链接（留空则页脚不展示） */
  readonly VITE_WEBMASTER_NAME?: string
  readonly VITE_WEBMASTER_URL?: string
  /** 联系邮箱 */
  readonly VITE_CONTACT_EMAIL?: string
  /** ICP 备案号与查询链接（留空则页脚不展示） */
  readonly VITE_ICP_NUMBER?: string
  readonly VITE_ICP_URL?: string
  /** 公安备案号与查询链接（留空则页脚不展示；备案码从备案号数字段提取） */
  readonly VITE_GA_BEIAN_NUMBER?: string
  readonly VITE_GA_BEIAN_URL?: string
  /** 公安备案标识图片 URL（留空则仅展示文字） */
  readonly VITE_GA_BEIAN_ICON?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
