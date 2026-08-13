/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 应用部署域名（部署后对外访问的地址） */
  readonly VITE_APP_DEPLOY_BASE_URL: string
  /** 应用生成预览域名（预览 AI 生成的应用） */
  readonly VITE_APP_PREVIEW_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
