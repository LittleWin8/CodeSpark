<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ArrowUpOutlined,
  CheckCircleFilled,
  CloudUploadOutlined,
  DownOutlined,
  LoadingOutlined,
  PaperClipOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import { deployApp, getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, useCodeGenType } from '@/constants/codeGenType'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { renderMarkdown } from '@/utils/markdown'
import { getPreviewUrl } from '@/utils/url'
import { connectChatSse } from '@/utils/sse'
import systemLogo from '@/assets/logo.png'
type ChatRole = 'user' | 'ai'

// 生成模式展示工具
const { label: codeGenTypeLabel, color: codeGenTypeColor } = useCodeGenType()

interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  files?: string[]
  done?: boolean
  /** 后端创建时间，用于历史消息游标分页 */
  createTime?: string
  /** 是否为历史记录（区分当前会话实时生成的消息） */
  history?: boolean
  /** 内容是否被用户手动折叠 */
  collapsed?: boolean
  /** 模型推理（thinking）流式文本，仅实时生成时存在 */
  thinking?: string
  /** 推理是否结束（第一条正文到达即结束） */
  thinkingDone?: boolean
  /** 思考面板展开态 */
  thinkingExpanded?: boolean
  /** 首片思考到达时间戳（毫秒） */
  thinkingStart?: number
  /** 思考总耗时（秒），推理结束时冻结，避免完成后数字继续跳动 */
  thinkingElapsed?: number
  /** AI 消息的渲染块（文本块与文件写入卡片按流式顺序排列） */
  blocks?: MessageBlock[]
  /** 工具轮之间的"规划下一步"占位状态（模型静默推理 + API 往返期间亮起） */
  planningNext?: boolean
}

/** 文本块：AI 正文流 */
interface TextBlock {
  type: 'text'
  text: string
}

/** 文件写入卡片：由工具事件驱动（writing = 正在写入 / 已写入） */
interface ToolBlock {
  type: 'tool'
  path: string
  lang?: string
  content?: string
  writing: boolean
  /** 进入"正在写入"态的时间戳（毫秒），用于保证写入态最短可见时长 */
  writingStart?: number
  expanded?: boolean
}

type MessageBlock = TextBlock | ToolBlock

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const app = ref<API.AppVO>()
const loading = ref(true)
const nameLoading = ref(false)
const deploying = ref(false)
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

/** 思考实时读秒（由 nowTick 驱动，思考期间每秒 +1） */
const liveThinkingSeconds = (msg: ChatMessage) =>
  msg.thinkingStart ? Math.max(1, Math.round((nowTick.value - msg.thinkingStart) / 1000)) : 0

/** 思考读秒的"当前时刻"：思考期间每秒跳动一次，驱动面板时间实时更新 */
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

// VUE 工程预览：后端在生成完成后异步构建（npm install + build），
// 需轮询预览地址直到可访问再展示 iframe
const previewReady = ref(true)
let previewPollTimer: ReturnType<typeof setInterval> | null = null
const VUE_PREVIEW_POLL_INTERVAL = 2000
const VUE_PREVIEW_POLL_MAX = 90 // 最长约 3 分钟

const stopVuePreviewPolling = () => {
  if (previewPollTimer) {
    clearInterval(previewPollTimer)
    previewPollTimer = null
  }
}

const pollVuePreviewReady = () => {
  const url = previewUrl.value
  if (!url || app.value?.codeGenType !== CodeGenTypeEnum.VUE_PROJECT) {
    previewReady.value = true
    return
  }
  previewReady.value = false
  let count = 0
  previewPollTimer = setInterval(async () => {
    count++
    let ok = false
    try {
      const res = await fetch(url, { method: 'GET', credentials: 'include' })
      ok = res.ok
    } catch {
      ok = false
    }
    if (ok || count >= VUE_PREVIEW_POLL_MAX) {
      previewReady.value = true
      stopVuePreviewPolling()
    }
  }, VUE_PREVIEW_POLL_INTERVAL)
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
 * - VUE_PROJECT：来自工具事件（FileWriteTool 实际写入的相对路径，渲染为卡片）
 * - MULTI_FILE：后端 CodeFileSaver 固定保存 index.html / style.css / script.js
 *   （按代码围栏判定；CSS/JS 内容为空时后端不会落盘，此处保守地只在有对应围栏时列出）
 * - HTML：后端固定保存 index.html
 */
const extractFiles = (msg: ChatMessage): string[] => {
  const files: string[] = []
  for (const block of msg.blocks ?? []) {
    if (block.type === 'tool' && block.path) {
      files.push(block.path)
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

/** 渲染块兜底：无块时把 content 当作单个文本块 */
const blocksOf = (msg: ChatMessage): MessageBlock[] =>
  msg.blocks?.length
    ? msg.blocks
    : msg.content
      ? [{ type: 'text', text: msg.content }]
      : []

/** 文件写入卡片数量（折叠时的摘要提示） */
const toolCardCount = (msg: ChatMessage): number =>
  (msg.blocks ?? []).filter((block) => block.type === 'tool').length

/**
 * 解析历史消息文本为渲染块
 * 历史持久化格式：正文 + `[工具调用] 写入文件 <path>\n```<lang>\n<content>\n```` 文本块
 */
const parseMessageBlocks = (text: string): MessageBlock[] => {
  if (!text) {
    return [{ type: 'text', text: '' }]
  }
  const blocks: MessageBlock[] = []
  const toolBlockRe = /\[工具调用\] 写入文件\s+([^\s]+)\n```(\w*)\n([\s\S]*?)```/g
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = toolBlockRe.exec(text)) !== null) {
    if (match.index > lastIndex) {
      blocks.push({ type: 'text', text: text.slice(lastIndex, match.index) })
    }
    blocks.push({
      type: 'tool',
      path: match[1],
      lang: match[2] || '',
      content: match[3] || '',
      writing: false,
    })
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

const isFolded = (msg: ChatMessage): boolean => Boolean(msg.collapsed)

const toggleCollapse = (msg: ChatMessage) => {
  msg.collapsed = !msg.collapsed
}

/** 内容超过 FOLD_LINE_COUNT 行时提供折叠/展开按钮 */
const shouldShowFoldBtn = (msg: ChatMessage): boolean =>
  contentLineCount(msgText(msg)) > FOLD_LINE_COUNT

/** 折叠时展示的内容：前 FOLD_LINE_COUNT 行 */
const foldedContent = (msg: ChatMessage): string =>
  msgText(msg)
    .split('\n')
    .slice(0, FOLD_LINE_COUNT)
    .join('\n')

/** 单个文本块渲染的 Markdown HTML（空内容给占位符） */
const blockHtml = (text: string): string => (text ? renderMarkdown(text) : '-')

const closeSse = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

const sendMessage = async (text: string) => {
  const messageText = text.trim()
  if (!messageText || !app.value?.id || generating.value || !canChat.value) {
    return
  }

  closeSse()
  generating.value = true
  showPreview.value = false

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
  await scrollToBottom(true)

  const aiIndex = messages.value.length - 1

  eventSource = connectChatSse(app.value.id, messageText, {
    onMessage: (chunk) => {
      const current = messages.value[aiIndex]
      if (!current) {
        return
      }
      // 正文追加到最后一个文本块（若上一个是工具卡片则新建文本块，保持顺序）
      const blocks = (current.blocks ??= [])
      const last = blocks[blocks.length - 1]
      if (!last || last.type !== 'text') {
        blocks.push({ type: 'text', text: chunk })
      } else {
        last.text += chunk
      }
      current.content = msgText(current)
      // 第一条正文到达 = 推理结束：冻结思考耗时，自动收起思考面板
      if (!current.thinkingDone) {
        current.thinkingElapsed = thinkingSeconds(current)
      }
      current.thinkingDone = true
      current.thinkingExpanded = false
      current.planningNext = false
      current.files = extractFiles(current)
      scrollToBottom()
    },
    onThinking: (chunk) => {
      const current = messages.value[aiIndex]
      if (!current) {
        return
      }
      current.thinking = (current.thinking ?? '') + chunk
      current.thinkingStart = current.thinkingStart ?? Date.now()
      current.planningNext = false
      startThinkingTimer() // 思考期间每秒跳动，驱动面板实时读秒
      // 正文未到时跟随思考滚动，让用户看到"正在思考"的状态
      if (!current.content) {
        scrollToBottom()
      }
    },
    onToolRequest: (payload) => {
      const current = messages.value[aiIndex]
      if (!current || !payload.path) {
        return
      }
      const blocks = (current.blocks ??= [])
      // 连续重复的写入请求（同一路径）不重复建卡
      const last = blocks[blocks.length - 1]
      if (last && last.type === 'tool' && last.writing && last.path === payload.path) {
        return
      }
      blocks.push({
        type: 'tool',
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
      const blocks = (current.blocks ??= [])
      // 找到最后一个同路径的"写入中"卡片；没有则新建（tool_request 可能因时序太快未被感知到）
      let card = [...blocks]
        .reverse()
        .find((b) => b.type === 'tool' && b.writing && b.path === payload.path)
      if (!card || card.type !== 'tool') {
        card = { type: 'tool', path: payload.path, writing: true, writingStart: Date.now() }
        blocks.push(card)
      }
      card.lang = payload.lang
      card.content = payload.content
      // 保证"正在写入"状态有最小可见时长（文件写入通常只需几毫秒，一闪而过用户感知不到）
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
      showPreview.value = true
      previewKey.value += 1
      scrollToBottom()
      // 生成完成后再同步一次应用信息，确保名称等字段是最新的
      syncApp()
      // VUE 构建是异步的，轮询预览地址直到可访问
      if (app.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
        pollVuePreviewReady()
      }
    },
    onError: () => {
      generating.value = false
      stopThinkingTimer()
      const current = messages.value[aiIndex]
      if (current) {
        current.planningNext = false
        if (!current.content) {
          current.content = t('appChat.sendFailed')
          current.done = true
        }
      }
      message.error(t('appChat.sendFailed'))
    },
  })
}

const handleSend = () => {
  sendMessage(inputMessage.value)
}

const handleBack = () => {
  // 有上一页历史时返回上一页，直接打开链接（无历史）时回首页
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
  } finally {
    deploying.value = false
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
  stopVuePreviewPolling()
  previewReady.value = true
  messages.value = []
  hasMoreHistory.value = false
  showPreview.value = false
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
  stopVuePreviewPolling()
  stopThinkingTimer()
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
            {{ t('common.edit') }}
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
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="message-row"
              :class="msg.role === 'user' ? 'message-row--user' : 'message-row--ai'"
            >
              <!-- 头像（左侧：AI logo；右侧：用户头像） -->
              <div v-if="msg.role === 'ai'" class="message-avatar">
                <img class="message-avatar__img" :src="systemLogo" alt="CodeSpark AI" />
              </div>
              <div v-else class="message-avatar">
                <img
                  v-if="userMessageAvatar"
                  class="message-avatar__img"
                  :src="userMessageAvatar"
                  alt=""
                />
                <span v-else class="message-avatar__fallback">
                  {{ userMessageName?.slice(0, 1) || 'U' }}
                </span>
              </div>

              <!-- 昵称 + 气泡 -->
              <div class="message-body">
                <div class="message-nickname">
                  {{ msg.role === 'ai' ? t('appChat.aiName') : userMessageName }}
                </div>
                <div class="message-bubble" :class="msg.role === 'user' ? 'is-user' : 'is-ai'">
                  <!-- 深度思考面板：推理中实时展示，正文到达后自动收起为"已深度思考 N 秒"，点击可展开回顾 -->
                  <div v-if="msg.role === 'ai' && msg.thinking" class="thinking-panel-wrap">
                    <div
                      class="thinking-panel"
                      :class="{ 'thinking-panel--done': msg.thinkingDone }"
                      @click="msg.thinkingExpanded = !msg.thinkingExpanded"
                    >
                      <ThunderboltOutlined class="thinking-panel__icon" />
                      <span v-if="!msg.thinkingDone" class="thinking-panel__title">
                        {{ t('appChat.thinkingLive', { sec: liveThinkingSeconds(msg) }) }}
                      </span>
                      <span v-else class="thinking-panel__done-text">
                        {{ t('appChat.thinkingElapsed', { sec: thinkingSeconds(msg) }) }}
                      </span>
                      <DownOutlined v-if="!msg.thinkingExpanded" class="thinking-panel__arrow" />
                      <UpOutlined v-else class="thinking-panel__arrow" />
                    </div>
                    <div v-if="msg.thinkingExpanded" class="thinking-panel__body">
                      {{ msg.thinking }}
                    </div>
                  </div>
                  <!-- AI 消息：按块渲染（文本 + 文件写入卡片）；折叠时只展示前 N 行文本与文件摘要 -->
                  <template v-if="msg.role === 'ai'">
                    <template v-if="isFolded(msg)">
                      <div class="message-content">{{ foldedContent(msg) }}</div>
                      <div v-if="toolCardCount(msg)" class="message-fold-hint">
                        {{ t('appChat.filesGenerated') }}（{{ toolCardCount(msg) }}）
                      </div>
                    </template>
                    <template v-else>
                      <template v-for="(block, bi) in blocksOf(msg)" :key="bi">
                        <div
                          v-if="block.type === 'text'"
                          class="message-content message-content--md"
                        >
                          <!-- 流式与完成态统一走 Markdown 渲染：代码块随分片实时生长，避免"全部生成后才渲染"的割裂感 -->
                          <span v-if="block.text" v-html="blockHtml(block.text)"></span>
                          <template v-else>{{ bi === 0 ? t('appChat.generating') : '' }}</template>
                        </div>
                        <div v-else class="tool-card">
                          <div class="tool-card__head">
                            <LoadingOutlined
                              v-if="block.writing"
                              spin
                              class="tool-card__icon tool-card__icon--writing"
                            />
                            <CheckCircleFilled v-else class="tool-card__icon tool-card__icon--done" />
                            <span class="tool-card__path">{{ block.path }}</span>
                            <span
                              class="tool-card__status"
                              :class="block.writing ? 'is-writing' : 'is-done'"
                            >
                              {{ block.writing ? t('appChat.writingFile') : t('appChat.wroteFile') }}
                            </span>
                            <a-button
                              v-if="!block.writing && block.content"
                              type="link"
                              size="small"
                              class="tool-card__toggle"
                              @click="block.expanded = !block.expanded"
                            >
                              {{ block.expanded ? t('appChat.collapse') : t('appChat.viewCode') }}
                            </a-button>
                          </div>
                          <pre v-if="!block.writing && block.expanded" class="tool-card__code"><code>{{
                            block.content
                          }}</code></pre>
                        </div>
                      </template>
                      <!-- 工具轮之间的间隔占位：模型静默规划下一步 + API 往返期间亮起 -->
                      <div v-if="msg.planningNext && !msg.done" class="planning-indicator">
                        <LoadingOutlined spin class="planning-indicator__icon" />
                        <span>{{ t('appChat.planningNext') }}</span>
                      </div>
                    </template>
                  </template>
                  <!-- 用户消息：折叠时展示前 10 行 -->
                  <div v-else class="message-content">
                    {{ isFolded(msg) ? foldedContent(msg) : msg.content }}
                  </div>

                  <div v-if="msg.files?.length && !isFolded(msg)" class="file-list">
                    <div class="file-list__title">{{ t('appChat.filesGenerated') }}</div>
                    <div v-for="file in msg.files" :key="file" class="file-item">
                      <SettingOutlined />
                      <span>{{ file }}</span>
                    </div>
                  </div>

                  <!-- AI 消息底部：实时完成的显示「已保存」，超过 10 行提供折叠按钮 -->
                  <div
                    v-if="
                      msg.role === 'ai' && ((msg.done && !msg.history) || shouldShowFoldBtn(msg))
                    "
                    class="message-footer message-footer--ai"
                  >
                    <span v-if="msg.done && !msg.history" class="message-footer__saved">
                      {{ t('appChat.saved') }}
                    </span>
                    <a-button
                      v-if="shouldShowFoldBtn(msg)"
                      type="link"
                      size="small"
                      class="fold-btn"
                      @click="toggleCollapse(msg)"
                    >
                      <template #icon>
                        <DownOutlined v-if="isFolded(msg)" />
                        <UpOutlined v-else />
                      </template>
                      {{ isFolded(msg) ? t('appChat.expand') : t('appChat.collapse') }}
                    </a-button>
                  </div>
                  <!-- 用户消息：超过 10 行提供折叠/展开 -->
                  <div v-else-if="shouldShowFoldBtn(msg)" class="message-footer">
                    <a-button
                      type="link"
                      size="small"
                      class="fold-btn"
                      @click="toggleCollapse(msg)"
                    >
                      <template #icon>
                        <DownOutlined v-if="isFolded(msg)" />
                        <UpOutlined v-else />
                      </template>
                      {{ isFolded(msg) ? t('appChat.expand') : t('appChat.collapse') }}
                    </a-button>
                  </div>
                </div>
              </div>
            </div>
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
              <a-button type="primary" shape="circle" :loading="generating" @click="handleSend">
                <template #icon><ArrowUpOutlined /></template>
              </a-button>
            </div>
          </div>
        </section>

        <section class="preview-panel">
          <div class="preview-frame">
            <iframe
              v-if="showPreview && previewUrl && previewReady"
              :key="previewKey"
              class="preview-iframe"
              :src="previewUrl"
              :title="t('appChat.previewTitle')"
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

.message-row {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-row--user {
  justify-content: flex-end;
}

/* 用户消息：头像移到右侧 */
.message-row--user .message-avatar {
  order: 2;
}

.message-row--user .message-body {
  margin-right: 10px;
}

.message-row--ai {
  justify-content: flex-start;
  gap: 10px;
}

/* 圆形头像 */
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: #1f8f7a;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.message-avatar__fallback {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

/* 昵称 + 气泡列：限制最大宽度（宽屏也不超过 640px），保证代码等内容正常换行 */
.message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: min(70%, 640px);
}

/* 用户消息：昵称和气泡靠右 */
.message-row--user .message-body {
  align-items: flex-end;
}

.message-nickname {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 4px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-bubble {
  position: relative;
  border-radius: 14px;
  padding: 12px 14px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

/* AI 气泡左侧箭头（指向左侧头像） */
.message-bubble.is-ai::before {
  content: '';
  position: absolute;
  top: 14px;
  left: -8px;
  border: 5px solid transparent;
  border-right-color: #fff;
  border-left: 0;
}

/* 用户气泡右侧箭头（指向右侧头像） */
.message-bubble.is-user::before {
  content: '';
  position: absolute;
  top: 14px;
  right: -8px;
  border: 5px solid transparent;
  border-left-color: #e8e8ed;
  border-right: 0;
}

.message-bubble.is-user {
  background: #e8e8ed;
  color: #1d1d1f;
}

.message-bubble.is-ai {
  background: #fff;
  border: 1px solid #ececec;
}

/* 深度思考面板：推理过程展示（置灰、紧凑，与正文视觉分离） */
.thinking-panel-wrap {
  margin-bottom: 10px;
}

.thinking-panel {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 5px 10px;
  border-radius: 10px;
  background: #f4f5f7;
  color: #8a919f;
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
  user-select: none;
}

.thinking-panel--done {
  background: transparent;
  border: 1px dashed #e5e6eb;
}

.thinking-panel__icon {
  font-size: 13px;
  color: #a8abb2;
}

.thinking-panel__title {
  font-weight: 500;
}

.thinking-panel__done-text {
  color: #9aa0a6;
}

.thinking-panel__arrow {
  font-size: 10px;
  color: #b0b3b8;
}

.thinking-panel__body {
  margin-top: 6px;
  padding: 10px 12px;
  max-height: 240px;
  overflow-y: auto;
  border-radius: 10px;
  background: #f7f8fa;
  color: #8a919f;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
}

/* 文件写入卡片：正在写入 / 已写入状态 */
.tool-card {
  margin: 8px 0;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  background: #fafbfc;
  overflow: hidden;
}

.tool-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
}

.tool-card__icon {
  font-size: 14px;
  flex-shrink: 0;
}

.tool-card__icon--writing {
  color: #1677ff;
}

.tool-card__icon--done {
  color: #52c41a;
}

.tool-card__path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 500;
  color: #3c3f45;
}

.tool-card__status {
  font-size: 12px;
  flex-shrink: 0;
}

.tool-card__status.is-writing {
  color: #8a919f;
}

.tool-card__status.is-done {
  color: #52c41a;
}

.tool-card__toggle {
  padding: 0;
  font-size: 12px;
  flex-shrink: 0;
}

.tool-card__code {
  margin: 0;
  padding: 10px 12px;
  max-height: 260px;
  overflow: auto;
  background: #fff;
  border-top: 1px solid #f0f1f3;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-fold-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8a919f;
}

/* 工具轮间隔占位：模型静默规划/API 往返期间的轻量状态提示 */
.planning-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: #9aa0a6;
}

.planning-indicator__icon {
  font-size: 13px;
  color: #a8abb2;
}

.file-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-list__title {
  font-size: 12px;
  color: #8c8c8c;
}

.file-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f5f5f7;
  font-size: 13px;
}

.message-footer {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.message-footer--ai {
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-footer__saved {
  color: #8c8c8c;
}

.fold-btn {
  padding-inline: 0;
  height: auto;
  font-size: 12px;
}

.message-footer--ai .fold-btn {
  margin-left: auto;
}

/* Markdown 渲染内容（AI 输出） */
.message-content--md {
  white-space: normal;
}

.message-content--md :deep(p) {
  margin: 0 0 8px;
}

.message-content--md :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content--md :deep(h1),
.message-content--md :deep(h2),
.message-content--md :deep(h3),
.message-content--md :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.message-content--md :deep(h1) {
  font-size: 18px;
}

.message-content--md :deep(h2) {
  font-size: 16px;
}

.message-content--md :deep(h3),
.message-content--md :deep(h4) {
  font-size: 14px;
}

.message-content--md :deep(ul),
.message-content--md :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.message-content--md :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid #1f8f7a;
  color: #6b7280;
  background: #f5f5f7;
  border-radius: 0 6px 6px 0;
}

.message-content--md :deep(a) {
  color: #1f8f7a;
}

.message-content--md :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.message-content--md :deep(th),
.message-content--md :deep(td) {
  border: 1px solid #ececec;
  padding: 6px 10px;
}

/* 代码块（highlight.js 高亮后的 pre.hljs）：自动换行，不产生横向滚动 */
.message-content--md :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #ececec;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  font-size: 12px;
  line-height: 1.5;
}

.message-content--md :deep(pre code) {
  padding: 0;
  background: transparent;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
}

.message-content--md :deep(code) {
  padding: 2px 5px;
  background: rgba(27, 31, 35, 0.06);
  border-radius: 4px;
  font-size: 12px;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
}

.chat-input {
  margin: 0 16px 16px;
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: 16px;
  padding: 12px;
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
}

.preview-frame {
  height: 100%;
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
