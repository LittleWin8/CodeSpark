<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  DownOutlined,
  LoadingOutlined,
  PaperClipOutlined,
  SelectOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { deployApp, downloadAppCode, getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import MessageItem from '@/components/chat/MessageItem.vue'
import { CodeGenTypeEnum, useCodeGenType } from '@/constants/codeGenType'
import { useLoginUserStore } from '@/stores/loginUser'
import { useQuotaStore } from '@/stores/quota'
import { getErrorMessage, getBusinessErrorMessage } from '@/utils/errorMessage'
import { getPreviewUrl } from '@/utils/url'
import { connectBuildSse, connectChatSse } from '@/utils/sse'
import {
  buildElementPrompt,
  createVisualEditBridge,
  formatElementLabel,
  type SelectedElementInfo,
} from '@/utils/visualEdit'
import type { ChatMessage, MessageBlock, TextBlock } from '@/types/chat'

// 生成模式展示工具
const { label: codeGenTypeLabel, color: codeGenTypeColor } = useCodeGenType()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const quotaStore = useQuotaStore()

const app = ref<API.AppVO>()
const loading = ref(true)
const nameLoading = ref(false)
const deploying = ref(false)
const downloading = ref(false)
const generating = ref(false)
const showPreview = ref(false)
const previewKey = ref(0)
const inputMessage = ref('')
const messages = ref<ChatMessage[]>([])
const messageListRef = ref<HTMLElement>()
// 对话历史游标分页：一页 10 条
const HISTORY_PAGE_SIZE = 10
const historyLoading = ref(false)
const hasMoreHistory = ref(false)
let eventSource: EventSource | null = null

const appId = computed(() => String(route.params.id || ''))

const sameId = (a?: string | number, b?: string | number) => String(a ?? '') === String(b ?? '')

/** 思考耗时（秒）：推理结束时冻结（thinkingElapsed），进行中按时间戳实时计算 */
const thinkingSeconds = (msg: ChatMessage) =>
  msg.thinkingElapsed ??
  (msg.thinkingStart ? Math.max(1, Math.round((Date.now() - msg.thinkingStart) / 1000)) : 0)

/** 思考读秒的"当前时刻"：思考期间每秒跳动一次，驱动面板时间实时更新（透传给 MessageItem 实时读秒） */
const nowTick = ref(Date.now())
let thinkingTimer: ReturnType<typeof setInterval> | null = null
const startThinkingTimer = () => {
  if (thinkingTimer) {
    return
  }
  thinkingTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
}
const stopThinkingTimer = () => {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = null
  }
}

const isOwner = computed(() => {
  return Boolean(app.value?.userId && sameId(app.value.userId, loginUserStore.loginUser.id))
})

const canChat = computed(() => isOwner.value)

// 用户消息的昵称与头像：对话记录均为应用创建者发送（后端仅创建者可对话，管理员可查看），
// 统一展示创建者的信息，而不是当前登录用户（如管理员查看时不应显示管理员自己）
const userMessageName = computed(
  () => app.value?.user?.userName || loginUserStore.loginUser.userName,
)
const userMessageAvatar = computed(
  () => app.value?.user?.userAvatar || loginUserStore.loginUser.userAvatar,
)

const previewUrl = computed(() => getPreviewUrl(app.value?.codeGenType, app.value?.id))

// ---- 可视化编辑模式 ----
// 编辑模式下在预览 iframe 中点选元素，选中信息回显在输入框上方，并随消息一起发送给后端；
// iframe 注入与 postMessage 通信的复杂逻辑收敛在 utils/visualEdit.ts 中
const previewIframeRef = ref<HTMLIFrameElement>()
const editMode = ref(false)
const selectedElement = ref<SelectedElementInfo | null>(null)

const visualEditBridge = createVisualEditBridge({
  getIframe: () => previewIframeRef.value,
  onSelect: (info) => {
    selectedElement.value = info
  },
})

/** 有可用预览时才允许进入可视化编辑 */
const canVisualEdit = computed(
  () => Boolean(showPreview.value && previewUrl.value && previewReady.value && !generating.value),
)

/** 进入 / 退出可视化编辑模式 */
const handleToggleEditMode = () => {
  if (!editMode.value && !canVisualEdit.value) {
    message.warning(t('appChat.visualEditNoPreview'))
    return
  }
  editMode.value = !editMode.value
  visualEditBridge.setEnabled(editMode.value)
  if (!editMode.value) {
    selectedElement.value = null
  }
}

/** 移除已选中的元素（保留编辑模式，可继续点选其他元素） */
const clearSelectedElement = () => {
  selectedElement.value = null
  visualEditBridge.clearSelection()
}

/** 退出编辑模式并清除选中元素（发送消息后、离开页面时调用） */
const exitEditMode = () => {
  if (!editMode.value && !selectedElement.value) {
    return
  }
  editMode.value = false
  selectedElement.value = null
  visualEditBridge.setEnabled(false)
}

/** iframe 每次加载完成后注入元素选择行为（编辑模式开关由桥接器自动同步） */
const handleIframeLoad = () => {
  visualEditBridge.attach()
}

// VUE 工程预览：后端在生成完成后异步构建（npm install + build），
// 前端订阅构建状态 SSE（building / success / failed），收到终态后刷新预览，无需轮询
const previewReady = ref(true)
let buildSse: EventSource | null = null

const closeBuildSse = () => {
  if (buildSse) {
    buildSse.close()
    buildSse = null
  }
}

const startBuildWatch = () => {
  if (!app.value?.id) {
    return
  }
  closeBuildSse()
  // 进入"构建中"等待态，构建完成后自动挂载新预览
  previewReady.value = false
  buildSse = connectBuildSse(app.value.id, {
    onSuccess: () => {
      previewReady.value = true
      previewKey.value += 1
      closeBuildSse()
    },
    onFailed: (payload) => {
      previewReady.value = true
      closeBuildSse()
      message.error(payload?.message || t('appChat.buildFailed'))
    },
    onBusinessError: (payload) => {
      // 订阅时刻即被拒（如无权限），流未开始：直接关闭，避免自动重连空转
      previewReady.value = true
      closeBuildSse()
      message.error(getBusinessErrorMessage(payload?.code, payload?.message))
    },
  })
}

/** 距底部该阈值（px）内视为"在底部"，自动滚动跟随；用户上滑超过阈值则暂停跟随 */
const STICK_BOTTOM_THRESHOLD = 100
const stickToBottom = ref(true)

/** 滚动位置变化时更新"是否跟随底部"状态 */
const handleMessageScroll = () => {
  const el = messageListRef.value
  if (!el) {
    return
  }
  stickToBottom.value =
    el.scrollHeight - el.scrollTop - el.clientHeight <= STICK_BOTTOM_THRESHOLD
}

/**
 * 滚动到底部
 * @param force 强制滚动（发送消息、加载历史等需要定位到底的场景）；默认仅在用户停留在底部附近时跟随
 */
const scrollToBottom = async (force = false) => {
  await nextTick()
  const el = messageListRef.value
  if (!el) {
    return
  }
  // 用户上滑离开底部后不再自动拉回，避免打断阅读
  if (!force && !stickToBottom.value) {
    return
  }
  el.scrollTop = el.scrollHeight
}

/**
 * 提取真实生成的文件列表（与后端落盘行为对齐）：
 * - VUE_PROJECT：来自工具事件（FileWriteTool 实际写入的相对路径，渲染为卡片；
 *   仅统计 writeFile / modifyFile 这类产出或修改文件的工具，readDir/readFile 是读取操作不产出文件；
 *   无 name 的历史块按原逻辑兜底计入）
 * - MULTI_FILE：后端 CodeFileSaver 固定保存 index.html / style.css / script.js
 *   （按代码围栏判定；CSS/JS 内容为空时后端不会落盘，此处保守地只在有对应围栏时列出）
 * - HTML：后端固定保存 index.html
 */
const extractFiles = (msg: ChatMessage): string[] => {
  const files: string[] = []
  for (const block of msg.blocks ?? []) {
    if (block.type === 'tool' && block.path) {
      if (!block.name || block.name === 'writeFile' || block.name === 'modifyFile') {
        files.push(block.path)
      }
    }
  }
  const text = msgText(msg)
  if (/```html/i.test(text) || /<!doctype html/i.test(text)) {
    files.push('index.html')
  }
  if (/```css/i.test(text)) {
    files.push('style.css')
  }
  if (/```(?:js|javascript)/i.test(text)) {
    files.push('script.js')
  }
  // 去重，保持首次出现顺序
  return [...new Set(files)]
}

/** AI 消息的纯文本（全部文本块拼接；无块时回退 content） */
const msgText = (msg: ChatMessage): string =>
  msg.blocks
    ?.filter((block): block is TextBlock => block.type === 'text')
    .map((block) => block.text)
    .join('') ?? msg.content

/**
 * AI 消息失败收尾（连接失败 / business-error 共用）：错误文案写进首个文本块
 * （气泡渲染用的是 blocks，只写 content 占位符消不掉），冻结思考耗时并收起面板，
 * 未完成的工具卡片全部置完成，避免"正在生成..."占位与转圈卡片残留。
 * 已有部分正文时予以保留，不覆盖。
 */
const finishAiMessageWithError = (aiIndex: number, errText: string) => {
  const current = messages.value[aiIndex]
  if (!current) {
    return
  }
  current.planningNext = false
  current.done = true
  current.thinkingDone = true
  current.thinkingElapsed = current.thinkingElapsed ?? thinkingSeconds(current)
  current.thinkingExpanded = false
  for (const block of current.blocks ?? []) {
    if (block.type === 'tool' && block.writing) {
      block.writing = false
    }
  }
  const textBlocks = (current.blocks ?? []).filter(
    (block): block is TextBlock => block.type === 'text',
  )
  if (textBlocks.length === 0) {
    current.blocks = [{ type: 'text', text: errText }]
  } else if (!textBlocks.some((block) => block.text)) {
    // 全是空文本块（零分片失败）：首块写入错误文案，顶掉"正在生成..."占位
    textBlocks[0].text = errText
  }
  current.content = msgText(current)
  current.files = extractFiles(current)
}

/**
 * 解析历史消息文本为渲染块
 * 历史持久化格式：正文 + 工具调用文本块：
 * - 写文件：`[工具调用] (writeFile|写入文件) <path>\n```<lang>\n<content>\n````（兼容新旧两种写法）
 * - 其他工具：`[工具调用] <toolName> <path>`
 */
const parseMessageBlocks = (text: string): MessageBlock[] => {
  if (!text) {
    return [{ type: 'text', text: '' }]
  }
  const blocks: MessageBlock[] = []
  const toolBlockRe =
    /\[工具调用\]\s+(?:(?:写入文件|writeFile)\s+([^\s]+)\n```(\w*)\n([\s\S]*?)```|(\w+)\s+([^\s]+))/g
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = toolBlockRe.exec(text)) !== null) {
    if (match.index > lastIndex) {
      blocks.push({ type: 'text', text: text.slice(lastIndex, match.index) })
    }
    if (match[1] !== undefined) {
      // 写入文件（带代码围栏）：可展开查看完整内容
      blocks.push({
        type: 'tool',
        name: 'writeFile',
        path: match[1],
        lang: match[2] || '',
        content: match[3] || '',
        writing: false,
      })
    } else {
      // 其他工具：仅记录工具名与路径
      blocks.push({ type: 'tool', name: match[4], path: match[5], writing: false })
    }
    lastIndex = match.index + match[0].length
  }
  const tail = text.slice(lastIndex)
  if (tail) {
    blocks.push({ type: 'text', text: tail })
  } else if (blocks.length === 0) {
    blocks.push({ type: 'text', text })
  }
  return blocks
}

// 内容超过该行数才显示收起按钮，收起时展示前 N 行内容
const FOLD_LINE_COUNT = 10

/** 文件写入卡片的"正在写入"态最短可见时长（ms）：文件写入本身只需几毫秒，太短用户感知不到 */
const MIN_WRITING_MS = 400

const contentLineCount = (content: string): number => content.split('\n').length

const closeSse = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

const sendMessage = async (text: string) => {
  let messageText = text.trim()
  if (!messageText || !app.value?.id || generating.value || !canChat.value) {
    return
  }
  // 可视化编辑：将用户选中的元素信息附加到提示词中，随消息一起发送给后端
  if (selectedElement.value) {
    messageText += buildElementPrompt(selectedElement.value)
  }

  closeSse()
  generating.value = true

  const userMsg: ChatMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: messageText,
    done: true,
    // 超过 10 行的用户消息默认折叠
    collapsed: contentLineCount(messageText) > FOLD_LINE_COUNT,
  }
  const aiMsg: ChatMessage = {
    id: `ai-${Date.now()}`,
    role: 'ai',
    content: '',
    files: [],
    done: false,
    collapsed: false,
    thinking: '',
    thinkingDone: false,
    thinkingExpanded: true,
    planningNext: false,
    blocks: [{ type: 'text', text: '' }],
  }
  messages.value.push(userMsg, aiMsg)
  inputMessage.value = ''
  // 发送后清除选中元素并退出编辑模式
  exitEditMode()
  await scrollToBottom(true)

  const aiIndex = messages.value.length - 1

  // ---- 思考分片节流 ----
  // DeepSeek 推理阶段 chunk 极密（每几 token 一个分片），逐 chunk 更新响应式 thinking 会触发
  // 整个页面组件重渲染，思考文本越长、对话历史越多，主线程越容易被占满（表现为"思考时卡死"）。
  // 这里先把分片累积到本地数组，按固定间隔（100ms）批量 flush，重渲染频率从每秒数百次降到约 10 次；
  // 数组累积 + 一次性 join 也避免了逐 chunk 字符串拼接的 O(n²) 开销。
  const thinkingChunks: string[] = []
  let thinkingFlushTimer: ReturnType<typeof setTimeout> | null = null
  const flushThinking = (scroll: boolean) => {
    if (thinkingFlushTimer) {
      clearTimeout(thinkingFlushTimer)
      thinkingFlushTimer = null
    }
    const current = messages.value[aiIndex]
    if (current && thinkingChunks.length) {
      current.thinking = (current.thinking ?? '') + thinkingChunks.join('')
      thinkingChunks.length = 0
      // 正文未到时跟随思考滚动，让用户看到"正在思考"的状态（随节流低频执行，避免频繁布局）
      if (scroll && !current.content) {
        scrollToBottom()
      }
    }
  }

  // business-error 到达后连接会关闭，不再需要通用的连接失败提示；标记位防重
  let businessErrorReceived = false

  // ---- 正文分片节流 ----
  // 正文 chunk 同样极密，逐 chunk 做「全量重拼 content + 全文正则扫文件 + 重渲染 + 强制布局滚动」
  // 会让主线程饱和，滚动时浏览器来不及绘制，表现为"只显示窗口一段、上下滚动空白"。
  // 这里把 chunk 累积到本地缓冲，按固定间隔批量 flush 一次，并增量维护 content，避免 O(n²)。
  const contentBuffer: string[] = []
  let contentFlushTimer: ReturnType<typeof setTimeout> | null = null

  const flushContent = (scroll: boolean) => {
    if (contentFlushTimer) {
      clearTimeout(contentFlushTimer)
      contentFlushTimer = null
    }
    const current = messages.value[aiIndex]
    if (!current || contentBuffer.length === 0) {
      return
    }
    const appended = contentBuffer.join('')
    contentBuffer.length = 0
    // 追加到最后一个文本块（上一个是工具卡片则新建文本块，保持顺序）
    const blocks = (current.blocks ??= [])
    const last = blocks[blocks.length - 1]
    if (!last || last.type !== 'text') {
      blocks.push({ type: 'text', text: appended })
    } else {
      last.text += appended
    }
    // 增量维护 content，避免每次全量 filter + join
    current.content = (current.content ?? '') + appended
    // 第一条正文到达 = 推理结束：冻结思考耗时，自动收起思考面板
    if (!current.thinkingDone) {
      current.thinkingElapsed = thinkingSeconds(current)
    }
    current.thinkingDone = true
    current.thinkingExpanded = false
    current.planningNext = false
    if (scroll) {
      scrollToBottom()
    }
  }

  eventSource = connectChatSse(app.value.id, messageText, {
    onMessage: (chunk) => {
      const current = messages.value[aiIndex]
      if (!current) {
        return
      }
      // 正文分片先进缓冲，按固定间隔批量 flush（见 flushContent 注释），
      // 避免逐 chunk 重拼文本/扫全文正则/触发整段重渲染
      contentBuffer.push(chunk)
      if (!contentFlushTimer) {
        contentFlushTimer = setTimeout(() => flushContent(true), 100)
      }
    },
    onThinking: (chunk) => {
      const current = messages.value[aiIndex]
      if (!current) {
        return
      }
      current.thinkingStart = current.thinkingStart ?? Date.now()
      current.planningNext = false
      startThinkingTimer() // 思考期间每秒跳动，驱动面板实时读秒
      // 累积分片，节流批量刷新（见上方 flushThinking 注释），避免每 chunk 触发整页重渲染
      thinkingChunks.push(chunk)
      if (!thinkingFlushTimer) {
        thinkingFlushTimer = setTimeout(() => flushThinking(true), 100)
      }
    },
    onToolRequest: (payload) => {
      const current = messages.value[aiIndex]
      if (!current || !payload.path) {
        return
      }
      // 先落盘缓冲的正文，保证文本块排在工具卡片之前（否则顺序会倒置）
      flushContent(false)
      const blocks = (current.blocks ??= [])
      // 连续重复的同一工具请求（同工具 + 同路径）不重复建卡
      const last = blocks[blocks.length - 1]
      if (
        last &&
        last.type === 'tool' &&
        last.writing &&
        last.path === payload.path &&
        last.name === payload.name
      ) {
        return
      }
      blocks.push({
        type: 'tool',
        name: payload.name,
        path: payload.path,
        writing: true,
        writingStart: Date.now(),
      })
      current.planningNext = false
      scrollToBottom()
    },
    onToolExecuted: (payload) => {
      const current = messages.value[aiIndex]
      if (!current || !payload.path) {
        return
      }
      // 先落盘缓冲的正文，避免文本块与工具卡片顺序倒置
      flushContent(false)
      const blocks = (current.blocks ??= [])
      // 找到最后一个同工具同路径的"执行中"卡片；没有则新建（tool_request 可能因时序太快未被感知到）
      let card = [...blocks]
        .reverse()
        .find(
          (b) =>
            b.type === 'tool' && b.writing && b.path === payload.path && b.name === payload.name,
        )
      if (!card || card.type !== 'tool') {
        card = {
          type: 'tool',
          name: payload.name,
          path: payload.path,
          writing: true,
          writingStart: Date.now(),
        }
        blocks.push(card)
      }
      card.lang = payload.lang
      card.content = payload.content
      // 保证"正在执行"状态有最小可见时长（文件操作通常只需几毫秒，一闪而过用户感知不到）
      const wait = Math.max(0, MIN_WRITING_MS - (Date.now() - (card.writingStart ?? Date.now())))
      setTimeout(() => {
        if (card && card.type === 'tool' && card.writing) {
          card.writing = false
        }
      }, wait)
      // 工具执行完成 → 新一轮 API 请求开始前的间隔（模型静默规划 + 网络往返），亮起占位提示
      current.planningNext = true
      current.files = extractFiles(current)
      scrollToBottom()
    },
    onDone: () => {
      // 先把尚未落盘的思考分片与正文分片合并进消息，避免尾部内容丢失
      flushThinking(false)
      flushContent(false)
      const current = messages.value[aiIndex]
      if (current) {
        current.done = true
        current.thinkingDone = true
        current.thinkingElapsed = current.thinkingElapsed ?? thinkingSeconds(current)
        // 兜底：未完成的写入卡片全部置为完成态
        for (const block of current.blocks ?? []) {
          if (block.type === 'tool' && block.writing) {
            block.writing = false
          }
        }
        current.content = msgText(current)
        if (!current.content) {
          current.content = t('appChat.aiThinking')
          current.blocks = [{ type: 'text', text: current.content }]
        }
        current.files = extractFiles(current)
      }
      generating.value = false
      stopThinkingTimer()
      if (current) {
        current.planningNext = false
      }
      // VUE 构建是异步的：订阅构建状态 SSE，构建完成后自动挂载新预览，
      // 避免旧产物/404 页面闪现，也无需用户手动刷新
      if (app.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
        startBuildWatch() // 内部先将 previewReady 置 false，进入构建等待态
      } else {
        // HTML/MULTI：无构建，直接刷新预览
        previewKey.value += 1
      }
      showPreview.value = true
      scrollToBottom()
      // 生成完成后再同步一次应用信息，确保名称等字段是最新的
      syncApp()
      // 生成会消耗额度：立即刷新一次，延迟再补一次（额度在响应结束时异步落库，防止竞态取到旧值）
      quotaStore.fetchQuota()
      setTimeout(() => quotaStore.fetchQuota(), 1200)
    },
    onBusinessError: (payload) => {
      // 流开始前即被拒（如限流）：收尾并关闭，不走成功链路；后续的连接关闭错误不再重复提示
      businessErrorReceived = true
      flushThinking(false)
      flushContent(false)
      generating.value = false
      stopThinkingTimer()
      closeSse()
      const errText = getBusinessErrorMessage(payload?.code, payload?.message)
      finishAiMessageWithError(aiIndex, errText)
      message.error(errText)
    },
    onError: () => {
      if (businessErrorReceived) {
        return
      }
      // 冲刷未落盘的思考与正文分片（失败时也保留已收到的内容）
      flushThinking(false)
      flushContent(false)
      generating.value = false
      stopThinkingTimer()
      // 按已收到多少细分原因：有过思考/正文/工具卡片说明中途断开，无任何回包则是根本没连上
      const current = messages.value[aiIndex]
      const hasPartial = !!(
        current &&
        (current.thinking ||
          (current.blocks ?? []).some((block) =>
            block.type === 'text' ? !!block.text : true,
          ))
      )
      const errText = t(hasPartial ? 'appChat.generateInterrupted' : 'appChat.connectFailed')
      finishAiMessageWithError(aiIndex, errText)
      message.error(errText)
    }
  })
}

const handleSend = () => {
  sendMessage(inputMessage.value)
}

const handleBack = () => {
  // 按进入来源返回：首页进入回首页，对话管理进入回对话管理页；
  // 其他来源（无 from 标记）走浏览器历史，无历史时兜底回首页
  const from = route.query.from as string | undefined
  if (from === 'manage') {
    router.push('/admin/chatHistoryManage')
    return
  }
  if (from === 'home') {
    router.push('/')
    return
  }
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push('/')
  }
}

const handleUpload = () => {
  message.info(t('appChat.uploadTip'))
}

const handleOptimize = () => {
  if (!inputMessage.value.trim()) {
    message.warning(t('appChat.optimizeTip'))
    return
  }
  const suffix = t('home.optimizeSuffix')
  if (!inputMessage.value.includes(suffix.trim())) {
    inputMessage.value = `${inputMessage.value.trim()}${suffix}`
  }
}

const handleDeploy = async () => {
  if (!app.value?.id) {
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId: app.value.id })
    if (res.data.code === 0 && res.data.data) {
      const url = res.data.data
      Modal.success({
        title: t('appChat.deploySuccess'),
        content: `${t('appChat.deployUrl')}: ${url}`,
        okText: t('common.open'),
        onOk: () => {
          window.open(url, '_blank')
        },
      })
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appChat.deployFailed'))
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { code?: number; message?: string } }; data?: { code?: number; message?: string } }
    const code = err?.response?.data?.code ?? err?.data?.code
    const msg = err?.response?.data?.message ?? err?.data?.message
    if (code || msg) {
      message.error(getErrorMessage(code, msg))
    } else {
      message.error(t('appChat.deployFailed'))
    }
  } finally {
    deploying.value = false
  }
}

const handleDownload = async () => {
  if (!app.value?.id) {
    return
  }
  downloading.value = true
  try {
    const res = await downloadAppCode(
      { appId: app.value.id },
      { responseType: 'blob' },
    )
    const contentType: string = String(res.headers?.['content-type'] ?? res.headers?.['Content-Type'] ?? '')
    const blob: Blob = res.data as Blob
    // 后端异常时，即使 responseType=blob，返回的仍是 JSON（Blob 形式），需解析并提示错误
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      try {
        const json = JSON.parse(text)
        message.error(getErrorMessage(json.code, json.message) || t('appChat.downloadFailed'))
      } catch {
        message.error(t('appChat.downloadFailed'))
      }
      return
    }
    if (contentType.includes('application/json')) {
      // 某些情况下 blob.type 可能为空，通过响应头判断
      try {
        const text = await blob.text()
        const json = JSON.parse(text)
        if (json.code !== undefined) {
          message.error(getErrorMessage(json.code, json.message) || t('appChat.downloadFailed'))
          return
        }
      } catch {
        // 非 JSON，继续走下载流程
      }
    }
    // 从 Content-Disposition 解析文件名：attachment; filename="xxx.zip"
    let filename = `${app.value.id}.zip`
    const disposition: string = String(
      res.headers?.['content-disposition'] ?? res.headers?.['Content-Disposition'] ?? '',
    )
    if (disposition) {
      const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
      if (utf8Match && utf8Match[1]) {
        try {
          filename = decodeURIComponent(utf8Match[1].replace(/"/g, ''))
        } catch {
          filename = utf8Match[1].replace(/"/g, '')
        }
      } else {
        const match = disposition.match(/filename="?([^"]+)"?/i)
        if (match && match[1]) {
          filename = match[1]
        }
      }
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    message.success(t('appChat.downloadSuccess'))
  } catch (error: unknown) {
    const err = error as {
      response?: { data?: Blob & { code?: number; message?: string }; headers?: Record<string, string>; status?: number }
    }
    const blob = err?.response?.data
    if (blob instanceof Blob) {
      try {
        const text = await blob.text()
        const json = JSON.parse(text)
        message.error(getErrorMessage(json.code, json.message) || t('appChat.downloadFailed'))
      } catch {
        message.error(t('appChat.downloadFailed'))
      }
    } else if (err?.response?.data) {
      const data = err.response.data as unknown as { code?: number; message?: string }
      message.error(getErrorMessage(data.code, data.message) || t('appChat.downloadFailed'))
    } else {
      message.error(t('appChat.downloadFailed'))
    }
  } finally {
    downloading.value = false
  }
}

const fetchApp = async () => {
  const requestId = appId.value
  if (!requestId) {
    message.error(t('appChat.loadFailed'))
    loading.value = false
    return
  }
  loading.value = true
  try {
    const res = await getAppVoById({ id: requestId as unknown as number })
    // 请求期间路由可能已切换，丢弃过期结果
    if (String(route.params.id || '') !== requestId) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appChat.loadFailed'))
      await router.replace('/')
    }
  } finally {
    loading.value = false
  }
}

/**
 * 轻量同步应用信息（不触发页面级 loading 转圈）
 * 用于代码生成完成后刷新名称等字段，避免轮询提前超时后名称不更新
 */
const syncApp = async () => {
  const requestId = appId.value
  if (!requestId) {
    return
  }
  try {
    const res = await getAppVoById({ id: requestId as unknown as number })
    // 请求期间路由可能已切换，丢弃过期结果
    if (String(route.params.id || '') !== requestId) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
    }
  } catch {
    // 同步失败静默处理，不影响主流程
  }
}

/**
 * 后端对话历史记录 → 页面消息
 */
const toChatMessage = (record: API.ChatHistory): ChatMessage => {
  const isAi = record.messageType === 'ai'
  const text = record.message ?? ''
  const msg: ChatMessage = {
    id: `history-${record.id ?? ''}`,
    role: isAi ? 'ai' : 'user',
    content: text,
    done: true,
    createTime: record.createTime,
    history: true,
    // 超过 10 行的用户历史消息默认折叠
    collapsed: !isAi && contentLineCount(text) > FOLD_LINE_COUNT,
  }
  if (isAi) {
    // 历史文本解析为渲染块：正文 + 文件写入卡片
    msg.blocks = parseMessageBlocks(text)
    msg.files = extractFiles(msg)
  }
  return msg
}

/**
 * 加载第一页对话历史（最近 10 条）
 * 接口按创建时间倒序返回，前端反转为升序展示，并保留最早一条的创建时间作为游标
 * @returns 是否加载成功
 */
const loadHistory = async (): Promise<boolean> => {
  const currentApp = app.value
  if (!currentApp?.id) {
    return false
  }
  const requestAppId = currentApp.id
  try {
    const res = await listAppChatHistory({ appId: requestAppId, pageSize: HISTORY_PAGE_SIZE })
    // 请求期间路由可能已切换，丢弃过期结果
    if (String(route.params.id || '') !== String(requestAppId)) {
      return false
    }
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      messages.value = [...records].reverse().map(toChatMessage)
      // 总数超过已加载条数时，说明还有更早的历史
      hasMoreHistory.value = (res.data.data.totalRow ?? 0) > records.length
      await scrollToBottom(true)
      return true
    }
    message.error(
      getErrorMessage(res.data.code, res.data.message) || t('appChat.historyLoadFailed'),
    )
  } catch {
    message.error(t('appChat.historyLoadFailed'))
  }
  return false
}

/**
 * 加载更早的一页历史消息（游标：当前已加载最早一条消息的创建时间）
 */
const loadMoreHistory = async () => {
  const currentApp = app.value
  if (!currentApp?.id || historyLoading.value || !hasMoreHistory.value) {
    return
  }
  const oldest = messages.value[0]
  if (!oldest?.createTime) {
    hasMoreHistory.value = false
    return
  }
  const requestAppId = currentApp.id
  historyLoading.value = true
  try {
    const res = await listAppChatHistory({
      appId: requestAppId,
      pageSize: HISTORY_PAGE_SIZE,
      lastCreateTime: oldest.createTime,
    })
    // 请求期间路由可能已切换，丢弃过期结果
    if (String(route.params.id || '') !== String(requestAppId)) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      const olderMessages = [...records].reverse().map(toChatMessage)
      // 记录插入前的滚动高度，加载后保持视口位置不跳动
      const el = messageListRef.value
      const prevScrollHeight = el?.scrollHeight ?? 0
      messages.value = [...olderMessages, ...messages.value]
      hasMoreHistory.value = (res.data.data.totalRow ?? 0) > records.length
      await nextTick()
      if (el) {
        el.scrollTop = el.scrollHeight - prevScrollHeight
      }
    } else {
      message.error(
        getErrorMessage(res.data.code, res.data.message) || t('appChat.historyLoadFailed'),
      )
    }
  } catch {
    message.error(t('appChat.historyLoadFailed'))
  } finally {
    historyLoading.value = false
  }
}

/**
 * 进入页面初始化：加载应用 + 第一页对话历史
 * - 已有至少 2 条对话记录时展示网站预览
 * - 自己的 app 且没有对话历史时，自动将 initPrompt 作为第一条消息触发对话
 */
const initChat = async () => {
  // 已离开对话页（路由参数变化为无 id）时不做初始化
  if (!route.params.id) {
    return
  }
  closeSse()
  stopNamePolling()
  closeBuildSse()
  previewReady.value = true
  messages.value = []
  hasMoreHistory.value = false
  showPreview.value = false
  exitEditMode()
  await fetchApp()
  if (!app.value?.id) {
    return
  }
  const historyLoaded = await loadHistory()
  if (messages.value.length >= 2) {
    showPreview.value = true
  }
  // 只有确认历史加载成功且为空，才自动发送初始消息，避免误发
  if (historyLoaded && isOwner.value && app.value?.initPrompt && messages.value.length === 0) {
    startNamePolling()
    await sendMessage(app.value.initPrompt)
  }
}

onMounted(initChat)

// 名称轮询：创建后 appName 是截取的，轮询直到 AI 异步生成新名称
// 命名由后端异步调用 AI 完成，耗时可能较长，最多等待 60 秒，超时则停止并显示当前名称
let namePollTimer: ReturnType<typeof setInterval> | null = null
const NAME_POLL_TIMEOUT = 60
const NAME_POLL_INTERVAL = 1000

const startNamePolling = () => {
  if (!app.value?.id || nameLoading.value) {
    return
  }
  nameLoading.value = true
  let pollCount = 0
  namePollTimer = setInterval(async () => {
    pollCount++
    const timeout = pollCount >= NAME_POLL_TIMEOUT
    try {
      const res = await getAppVoById({ id: app.value!.id as unknown as number })
      if (res.data.code === 0 && res.data.data) {
        const newApp = res.data.data
        // 名称变了（AI 生成完成）或超时 → 停止轮询
        if ((newApp.appName && newApp.appName !== app.value?.appName) || timeout) {
          app.value = newApp
          nameLoading.value = false
          stopNamePolling()
        }
      } else if (timeout) {
        nameLoading.value = false
        stopNamePolling()
      }
    } catch {
      if (timeout) {
        nameLoading.value = false
        stopNamePolling()
      }
    }
  }, NAME_POLL_INTERVAL)
}

const stopNamePolling = () => {
  if (namePollTimer) {
    clearInterval(namePollTimer)
    namePollTimer = null
  }
}

watch(
  () => route.params.id,
  async () => {
    await initChat()
  },
)

onBeforeUnmount(() => {
  closeSse()
  stopNamePolling()
  closeBuildSse()
  stopThinkingTimer()
  visualEditBridge.destroy()
})
</script>

<template>
  <a-spin :spinning="loading" class="chat-spin">
    <div id="appChatPage">
      <header class="chat-header">
        <div class="chat-header__left">
          <a-button type="text" class="back-btn" @click="handleBack">
            <template #icon><ArrowLeftOutlined /></template>
            {{ t('appChat.back') }}
          </a-button>
          <img class="chat-header__logo" src="@/assets/logo.png" alt="logo" />
          <div>
            <div class="chat-header__label">{{ t('appChat.appName') }}</div>
            <div v-if="nameLoading" class="chat-header__name chat-header__name--loading">
              <a-spin size="small" />
              <span class="name-loading-text">{{ t('appChat.nameGenerating') }}</span>
            </div>
            <div v-else class="chat-header__name">{{ app?.appName || '-' }}</div>
          </div>
        </div>
        <a-space>
          <!-- 当前生成模式标签 -->
          <a-tag class="mode-tag" :color="codeGenTypeColor(app?.codeGenType)">
            {{ codeGenTypeLabel(app?.codeGenType) }}
          </a-tag>
          <a-button v-if="canChat" @click="router.push(`/app/edit/${appId}`)">
            {{ t('appEdit.detail') }}
          </a-button>
          <a-button :loading="downloading" :disabled="!canChat || generating" @click="handleDownload">
            <template #icon><DownloadOutlined /></template>
            {{ t('appChat.download') }}
          </a-button>
          <a-popconfirm
            :title="t('appChat.deployConfirm')"
            :ok-text="t('common.confirm')"
            :cancel-text="t('common.cancel')"
            @confirm="handleDeploy"
          >
            <a-button type="primary" :loading="deploying" :disabled="!canChat || generating">
              <template #icon><CloudUploadOutlined /></template>
              {{ t('common.deploy') }}
            </a-button>
          </a-popconfirm>
        </a-space>
      </header>

      <div class="chat-body">
        <section class="chat-panel">
          <!-- 加载更早的历史消息 -->
          <div v-if="hasMoreHistory" class="history-more">
            <a-button type="link" :loading="historyLoading" @click="loadMoreHistory">
              {{ t('appChat.loadMore') }}
            </a-button>
          </div>
          <div ref="messageListRef" class="message-list" @scroll="handleMessageScroll">
            <MessageItem
              v-for="msg in messages"
              :key="msg.id"
              :msg="msg"
              :user-name="userMessageName"
              :user-avatar="userMessageAvatar"
              :now-tick="nowTick"
            />
          </div>

          <!-- 用户上滑阅读时，显示"回到底部"悬浮按钮；点击强制回到最新内容 -->
          <a-button
            v-if="!stickToBottom"
            class="scroll-to-bottom"
            shape="circle"
            size="small"
            :title="t('appChat.backToBottom')"
            @click="scrollToBottom(true)"
          >
            <template #icon><DownOutlined /></template>
          </a-button>

          <div v-if="canChat" class="chat-input">
            <!-- 可视化编辑：展示当前选中的元素信息，可手动移除 -->
            <a-alert
              v-if="selectedElement"
              class="chat-input__element-alert"
              type="info"
              closable
              @close="clearSelectedElement"
            >
              <template #message>
                {{ t('appChat.selectedElement') }}：{{ formatElementLabel(selectedElement) }}
              </template>
              <template #description>
                {{ selectedElement.selector
                }}<template v-if="selectedElement.textContent">
                  · "{{ selectedElement.textContent }}"</template
                >
              </template>
            </a-alert>
            <a-textarea
              v-model:value="inputMessage"
              :placeholder="t('appChat.inputPlaceholder')"
              :auto-size="{ minRows: 3, maxRows: 6 }"
              :bordered="false"
              :disabled="generating"
              @pressEnter.exact.prevent="handleSend"
            />
            <div class="chat-input__toolbar">
              <a-space>
                <a-button type="text" size="small" @click="handleUpload">
                  <template #icon><PaperClipOutlined /></template>
                  {{ t('common.upload') }}
                </a-button>
                <a-button type="text" size="small" @click="handleOptimize">
                  <template #icon><ThunderboltOutlined /></template>
                  {{ t('common.optimize') }}
                </a-button>
              </a-space>
              <a-space>
                <a-tooltip :title="editMode ? t('appChat.visualEditExit') : t('appChat.visualEdit')">
                  <a-button
                    shape="circle"
                    :type="editMode ? 'primary' : 'default'"
                    :disabled="generating"
                    @click="handleToggleEditMode"
                  >
                    <template #icon><SelectOutlined /></template>
                  </a-button>
                </a-tooltip>
                <a-button type="primary" shape="circle" :loading="generating" @click="handleSend">
                  <template #icon><ArrowUpOutlined /></template>
                </a-button>
              </a-space>
            </div>
          </div>
        </section>

        <section class="preview-panel">
          <!-- 可视化编辑模式提示：位于预览区顶部的提示条（右对齐），在 iframe 之外，不遮挡网页内容 -->
          <!-- 顶部提示条（二选一）：生成中提示勿离开（会中断生成），可视化编辑时提示点选 -->
          <div v-if="generating || editMode" class="preview-edit-tip">
            <WarningOutlined v-if="generating" class="preview-edit-tip__icon" />
            <SelectOutlined v-else class="preview-edit-tip__icon" />
            <span>{{ generating ? t('appChat.leaveInterruptsGen') : t('appChat.visualEditTip') }}</span>
          </div>
          <div class="preview-frame">
            <iframe
              v-if="showPreview && previewUrl && previewReady"
              ref="previewIframeRef"
              :key="previewKey"
              class="preview-iframe"
              :src="previewUrl"
              :title="t('appChat.previewTitle')"
              @load="handleIframeLoad"
            />
            <div v-else class="preview-empty">
              <!-- 生成中 / 构建中：骨架屏加载动画，缓解等待期的单调感 -->
              <div
                v-if="
                  generating || (app?.codeGenType === CodeGenTypeEnum.VUE_PROJECT && !previewReady)
                "
                class="preview-loading"
              >
                <div class="preview-loading__browser">
                  <div class="preview-loading__bar">
                    <i></i><i></i><i></i>
                  </div>
                  <div class="skeleton skeleton--line skeleton--w45"></div>
                  <div class="skeleton skeleton--line skeleton--w80"></div>
                  <div class="skeleton skeleton--line skeleton--w65"></div>
                  <div class="skeleton skeleton--line skeleton--w90"></div>
                  <div class="skeleton skeleton--line skeleton--w40"></div>
                </div>
                <div class="preview-loading__text">
                  <LoadingOutlined spin class="preview-loading__icon" />
                  <span>
                    {{ generating ? t('appChat.generating') : t('appChat.vueBuilding') }}
                  </span>
                </div>
              </div>
              <template v-else>{{ t('appChat.previewEmpty') }}</template>
            </div>
          </div>
        </section>
      </div>
    </div>
  </a-spin>
</template>

<style scoped>
.chat-spin {
  display: block;
  min-height: calc(100vh - 64px);
}

.chat-spin :deep(.ant-spin-container) {
  min-height: calc(100vh - 64px);
}

#appChatPage {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: #f5f5f7;
}

.chat-header {
  height: 64px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #ececec;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 生成模式标签：高度与按钮（32px）对齐 */
.mode-tag {
  height: 32px;
  line-height: 30px;
  margin-inline-end: 0;
  padding-inline: 12px;
  font-size: 14px;
}

.chat-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: #4b5563;
  margin-right: -4px;
}

.chat-header__logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.chat-header__label {
  font-size: 12px;
  color: #8c8c8c;
}

.chat-header__name {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
}

.chat-header__name--loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8c8c8c;
  font-weight: 400;
  font-size: 13px;
}

.name-loading-text {
  white-space: nowrap;
}

.chat-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 40% 60%;
}

.chat-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #ececec;
  background: #fafafa;
}

/* 回到底部悬浮按钮：用户上滑离开底部时出现 */
.scroll-to-bottom {
  position: absolute;
  right: 18px;
  bottom: 88px;
  z-index: 10;
  background: #fff;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  border-color: #e5e6eb;
}

.history-more {
  display: flex;
  justify-content: center;
  padding-top: 10px;
  flex-shrink: 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
}

.chat-input {
  margin: 0 16px 16px;
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: 16px;
  padding: 12px;
}

/* 可视化编辑：选中元素提示条 */
.chat-input__element-alert {
  margin-bottom: 8px;
  border-radius: 8px;
}

.chat-input__element-alert :deep(.ant-alert-description) {
  word-break: break-all;
}

.chat-input :deep(textarea) {
  resize: none;
  padding: 0;
}

.chat-input__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.preview-panel {
  min-width: 0;
  padding: 16px;
  background: #eef1f4;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 可视化编辑模式提示：预览区顶部的提示条（右对齐），作为独立一行排在 iframe 上方，
   不遮挡网页内容；深色底 + 白字保证任意背景下的可读性 */
.preview-edit-tip {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.65);
  color: #fff;
  font-size: 13px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  animation: preview-tip-in 0.25s ease;
}

.preview-edit-tip__icon {
  color: #69b1ff;
}

@keyframes preview-tip-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.preview-frame {
  flex: 1;
  min-height: 0;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.06);
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
  /* 渲染完成后淡入，避免生硬闪现 */
  animation: preview-fade-in 0.45s ease;
}

.preview-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
  padding: 24px;
  text-align: center;
}

/* 预览加载动画：浏览器窗口骨架屏 + 流光效果 */
.preview-loading {
  width: min(80%, 360px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.preview-loading__browser {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #e8eaed;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.preview-loading__bar {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
}

.preview-loading__bar i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #e3e5e8;
}

.preview-loading__bar i:nth-child(1) {
  background: #ffb3ab;
}
.preview-loading__bar i:nth-child(2) {
  background: #ffe08a;
}
.preview-loading__bar i:nth-child(3) {
  background: #b7e4c7;
}

.skeleton {
  height: 12px;
  margin-bottom: 9px;
  border-radius: 6px;
  background: linear-gradient(90deg, #eef0f3 25%, #f8f9fa 37%, #eef0f3 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

.skeleton--line {
  height: 12px;
}

.skeleton--w40 {
  width: 40%;
}
.skeleton--w45 {
  width: 45%;
}
.skeleton--w65 {
  width: 65%;
}
.skeleton--w80 {
  width: 80%;
}
.skeleton--w90 {
  width: 90%;
}

.preview-loading__text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #8a919f;
}

.preview-loading__icon {
  font-size: 16px;
  color: #a8abb2;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@keyframes preview-fade-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 960px) {
  .chat-body {
    grid-template-columns: 1fr;
    grid-template-rows: 50% 50%;
  }

  .chat-panel {
    border-right: none;
    border-bottom: 1px solid #ececec;
  }
}
</style>

