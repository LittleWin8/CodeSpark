import MarkdownIt from 'markdown-it'
import type { MarkdownIt as MarkdownItType } from 'markdown-it'
// 按需引入 highlight.js 核心与常用语言，避免全量打包
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('python', python)
hljs.registerLanguage('markdown', markdown)

/**
 * Markdown 渲染工具：用于渲染 AI 输出的内容（含代码块）
 * - html: false —— 不解析原始 HTML，直接转义，防止 AI 输出内容注入脚本（XSS）
 * - highlight —— 代码块交给 highlight.js 高亮
 */
const md: MarkdownItType = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(code, { language: lang }).value}</code></pre>`
      } catch {
        // 高亮失败时降级为转义文本
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
})

/**
 * 将 Markdown 文本渲染为安全的 HTML 字符串
 */
export function renderMarkdown(content: string): string {
  if (!content) {
    return ''
  }
  return md.render(content)
}
