// 根据后端接口生成前端请求和 TS 模型代码
export default {
  requestLibPath: "import request from '@/request'",
  schemaPath: 'http://localhost:8080/api/v3/api-docs',
  serversPath: './src',
  // 自定义生成模板：去掉自动生成文件顶部的 @ts-ignore（避免 ESLint 警告）
  templatesFolder: './openapi-templates',
}
