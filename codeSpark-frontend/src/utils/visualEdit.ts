/**
 * 可视化编辑（元素选择）工具
 * 主站与预览站点同域名：父页面直接向 iframe 注入元素选择行为，
 * iframe 内通过 postMessage 将用户选中的元素信息回传给主站。
 */

/** 用户在预览网站中选中的元素信息 */
export interface SelectedElementInfo {
  /** 标签名（小写），如 div / button */
  tagName: string
  /** 元素 id（无则空串） */
  id: string
  /** 元素 class（无则空串，多个类以空格分隔） */
  className: string
  /** 精简 CSS 选择器路径，如 body > div#app > button.btn */
  selector: string
  /** 元素文本摘要（压缩空白并截断） */
  textContent: string
  /** 元素所在页面路径（iframe 内 location.pathname） */
  pagePath: string
}

/** postMessage 消息来源标识（过滤无关消息） */
const MESSAGE_SOURCE = 'codespark-visual-edit'

/** 父页面 → iframe 的控制消息类型 */
const CMD_SET_ENABLED = 'set-enabled'
const CMD_CLEAR_SELECTION = 'clear-selection'

/** iframe → 父页面 的消息类型 */
const EVENT_ELEMENT_SELECTED = 'element-selected'

/** iframe 内部初始化标记（挂在 iframe window 上），避免重复注入 */
const IFRAME_INIT_FLAG = '__codesparkVisualEditInit__'

/** 编辑模式下 iframe 根元素挂载的 class，用于切换十字光标 */
const ACTIVE_CLASS = 'codespark-visual-edit-active'

/** 悬浮高亮（浅蓝）与选中高亮（深蓝固定） */
const HOVER_OUTLINE = '2px solid #69b1ff'
const SELECT_OUTLINE = '2px solid #0958d9'

/** 元素文本摘要最大长度 */
const TEXT_MAX_LENGTH = 80

/** 选择器路径最大层级，避免过长 */
const SELECTOR_MAX_DEPTH = 5

/** 注入目标：带初始化标记的 iframe window（交叉 typeof globalThis 以访问 HTMLElement 等全局构造器） */
type VisualEditIframeWindow = Window &
  typeof globalThis & { [IFRAME_INIT_FLAG]?: boolean }

/**
 * 向预览 iframe 注入元素选择行为（幂等，重复注入直接跳过）
 * 仅主站与 iframe 同域时可用；跨域访问 contentWindow 会抛错，静默返回
 *
 * @param iframe 预览 iframe 元素（需在 load 事件后调用）
 */
export function initVisualEditInIframe(iframe: HTMLIFrameElement): void {
  let iframeWin: VisualEditIframeWindow | null
  try {
    iframeWin = iframe.contentWindow as VisualEditIframeWindow | null
    // 触发一次同源校验：跨域时访问 document 会抛 SecurityError
    if (!iframeWin?.document) {
      return
    }
  } catch {
    return
  }
  if (iframeWin[IFRAME_INIT_FLAG]) {
    return
  }
  iframeWin[IFRAME_INIT_FLAG] = true
  // 转为 const，保证事件回调中的类型收窄稳定
  const win: VisualEditIframeWindow = iframeWin

  const doc = win.document
  /** 编辑模式开关（由父页面消息控制），关闭时所有事件监听直接放行 */
  let enabled = false
  /** 当前悬浮 / 选中的元素 */
  let hovered: HTMLElement | null = null
  let selected: HTMLElement | null = null
  /** 元素被覆盖前的原始 outline，取消高亮时还原，避免污染页面样式 */
  const outlineBackup = new WeakMap<HTMLElement, string>()

  // 编辑模式下使用十字光标，提示用户"当前处于点选状态"
  const style = doc.createElement('style')
  style.textContent = `.${ACTIVE_CLASS}, .${ACTIVE_CLASS} * { cursor: crosshair !important; }`
  doc.head.appendChild(style)

  /** 可选中元素：排除根元素 html（SVG 元素的 className 不是字符串，单独兜底） */
  const isSelectable = (el: EventTarget | null): el is HTMLElement =>
    el instanceof win.HTMLElement && el !== doc.documentElement

  const applyOutline = (el: HTMLElement, outline: string) => {
    if (!outlineBackup.has(el)) {
      outlineBackup.set(el, el.style.outline)
    }
    el.style.outline = outline
  }

  const restoreOutline = (el: HTMLElement | null) => {
    if (el && outlineBackup.has(el)) {
      el.style.outline = outlineBackup.get(el) ?? ''
      outlineBackup.delete(el)
    }
  }

  /** 自元素向上构建精简选择器路径；遇到 id 即停止（id 在页面内唯一） */
  const getSelector = (el: HTMLElement): string => {
    const parts: string[] = []
    let current: HTMLElement | null = el
    while (current && current !== doc.documentElement && parts.length < SELECTOR_MAX_DEPTH) {
      let part = current.tagName.toLowerCase()
      if (current.id) {
        part += `#${current.id}`
        parts.unshift(part)
        break
      }
      const classes =
        typeof current.className === 'string'
          ? current.className.trim().split(/\s+/).filter(Boolean)
          : []
      if (classes.length) {
        part += `.${classes.join('.')}`
      }
      parts.unshift(part)
      current = current.parentElement
    }
    return parts.join(' > ')
  }

  /** 收集元素信息并回传主站 */
  const collectInfo = (el: HTMLElement): SelectedElementInfo => ({
    tagName: el.tagName.toLowerCase(),
    id: el.id || '',
    className: typeof el.className === 'string' ? el.className.trim() : '',
    selector: getSelector(el),
    textContent: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, TEXT_MAX_LENGTH),
    pagePath: win.location.pathname,
  })

  const clearSelection = () => {
    restoreOutline(selected)
    selected = null
  }

  const setEnabled = (value: boolean) => {
    enabled = value
    doc.documentElement.classList.toggle(ACTIVE_CLASS, value)
    if (!value) {
      // 退出编辑模式：清除悬浮与选中高亮
      clearSelection()
      restoreOutline(hovered)
      hovered = null
    }
  }

  doc.addEventListener('mouseover', (event) => {
    if (!enabled || !isSelectable(event.target)) {
      return
    }
    hovered = event.target
    // 已选中的元素保持深色固定边框，不被悬浮样式覆盖
    if (hovered !== selected) {
      applyOutline(hovered, HOVER_OUTLINE)
    }
  })

  doc.addEventListener('mouseout', (event) => {
    if (!enabled || !isSelectable(event.target)) {
      return
    }
    const el = event.target
    if (el !== selected) {
      restoreOutline(el)
    }
    if (hovered === el) {
      hovered = null
    }
  })

  // 捕获阶段拦截点击：阻止链接跳转 / 按钮提交等页面自身交互
  doc.addEventListener(
    'click',
    (event) => {
      if (!enabled || !isSelectable(event.target)) {
        return
      }
      event.preventDefault()
      event.stopPropagation()
      const el = event.target
      if (selected && selected !== el) {
        restoreOutline(selected)
      }
      selected = el
      applyOutline(el, SELECT_OUTLINE)
      win.parent.postMessage(
        { source: MESSAGE_SOURCE, type: EVENT_ELEMENT_SELECTED, payload: collectInfo(el) },
        win.location.origin,
      )
    },
    true,
  )

  // 接收主站控制指令：开关编辑模式 / 清除选中
  win.addEventListener('message', (event: MessageEvent) => {
    if (event.source !== win.parent) {
      return
    }
    const data = event.data as { source?: string; type?: string; enabled?: boolean } | undefined
    if (!data || data.source !== MESSAGE_SOURCE) {
      return
    }
    if (data.type === CMD_SET_ENABLED) {
      setEnabled(Boolean(data.enabled))
    }
    if (data.type === CMD_CLEAR_SELECTION) {
      clearSelection()
    }
  })
}

/** 主站侧可视化编辑桥接器配置 */
export interface VisualEditBridgeOptions {
  /** 获取当前预览 iframe（重新渲染后可能更换，故每次动态获取） */
  getIframe: () => HTMLIFrameElement | null | undefined
  /** 用户在 iframe 中选中元素时回调 */
  onSelect: (info: SelectedElementInfo) => void
}

/** 主站侧可视化编辑桥接器 */
export interface VisualEditBridge {
  /** iframe 每次加载完成后调用：注入元素选择行为并同步编辑模式开关 */
  attach: () => void
  /** 开启 / 关闭编辑模式 */
  setEnabled: (enabled: boolean) => void
  /** 仅清除 iframe 内的选中高亮（不移除编辑模式） */
  clearSelection: () => void
  /** 销毁：移除全局消息监听 */
  destroy: () => void
}

/**
 * 创建主站侧的可视化编辑桥接器
 * 负责向 iframe 下发开关指令、接收 iframe 回传的选中元素信息
 */
export function createVisualEditBridge(options: VisualEditBridgeOptions): VisualEditBridge {
  let enabled = false

  const postToIframe = (message: Record<string, unknown>) => {
    const iframe = options.getIframe()
    try {
      iframe?.contentWindow?.postMessage(
        { source: MESSAGE_SOURCE, ...message },
        window.location.origin,
      )
    } catch {
      // iframe 跨域或未就绪时静默忽略
    }
  }

  const handleMessage = (event: MessageEvent) => {
    const data = event.data as
      | { source?: string; type?: string; payload?: SelectedElementInfo }
      | undefined
    if (!data || data.source !== MESSAGE_SOURCE) {
      return
    }
    if (data.type !== EVENT_ELEMENT_SELECTED || !data.payload) {
      return
    }
    // 注：编辑逻辑由主页面直接挂载到同源 iframe 的 document 上，点击处理函数运行在主页面
    // realm，其 postMessage 会被浏览器归为"主页面自身发出"（event.source === window），
    // 而非 iframe 的 contentWindow。因此这里不校验 event.source === iframe.contentWindow，
    // 仅靠唯一的 MESSAGE_SOURCE 标记 + 消息类型过滤，业务上也足够安全。
    options.onSelect(data.payload)
  }

  window.addEventListener('message', handleMessage)

  return {
    attach() {
      const iframe = options.getIframe()
      if (!iframe) {
        return
      }
      initVisualEditInIframe(iframe)
      // 同步当前编辑模式（iframe 内导航刷新后保持状态一致）
      postToIframe({ type: CMD_SET_ENABLED, enabled })
    },
    setEnabled(value: boolean) {
      enabled = value
      postToIframe({ type: CMD_SET_ENABLED, enabled: value })
    },
    clearSelection() {
      postToIframe({ type: CMD_CLEAR_SELECTION })
    },
    destroy() {
      window.removeEventListener('message', handleMessage)
    },
  }
}

/**
 * 将选中元素信息格式化为提示词片段（追加在用户消息之后发送给后端）
 */
export function buildElementPrompt(info: SelectedElementInfo): string {
  const lines = [
    '',
    '',
    '【选中的页面元素】',
    `- 页面路径：${info.pagePath}`,
    `- 元素标签：<${info.tagName}>`,
    `- CSS 选择器：${info.selector}`,
  ]
  if (info.id) {
    lines.push(`- 元素 ID：${info.id}`)
  }
  if (info.className) {
    lines.push(`- 元素类名：${info.className}`)
  }
  if (info.textContent) {
    lines.push(`- 文本内容：${info.textContent}`)
  }
  return lines.join('\n')
}

/**
 * 选中元素的紧凑标签（用于输入框上方的提示条），如 <button#submit> / <div.card>
 */
export function formatElementLabel(info: SelectedElementInfo): string {
  let label = `<${info.tagName}`
  if (info.id) {
    label += `#${info.id}`
  } else if (info.className) {
    label += `.${info.className.split(/\s+/).filter(Boolean).join('.')}`
  }
  return `${label}>`
}
