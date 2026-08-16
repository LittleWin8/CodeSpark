<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  DownOutlined,
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
}

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

const scrollToBottom = async () => {
  await nextTick()
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

const extractFiles = (content: string): string[] => {
  const files: string[] = []
  if (/```html/i.test(content)) {
    files.push('index.html')
  }
  if (/```css/i.test(content)) {
    files.push('style.css')
  }
  if (/```(?:js|javascript)/i.test(content)) {
    files.push('script.js')
  }
  return files
}

// 内容超过该行数才显示收起按钮，收起时展示前 N 行内容
const FOLD_LINE_COUNT = 10

const contentLineCount = (content: string): number => content.split('\n').length

const isFolded = (msg: ChatMessage): boolean => Boolean(msg.collapsed)

const toggleCollapse = (msg: ChatMessage) => {
  msg.collapsed = !msg.collapsed
}

/** 内容超过 FOLD_LINE_COUNT 行时提供折叠/展开按钮 */
const shouldShowFoldBtn = (msg: ChatMessage): boolean =>
  contentLineCount(msg.content) > FOLD_LINE_COUNT

/** 折叠时展示的内容：前 FOLD_LINE_COUNT 行 */
const foldedContent = (msg: ChatMessage): string =>
  msg.content.split('\n').slice(0, FOLD_LINE_COUNT).join('\n')

/** AI 消息渲染的 HTML：折叠时渲染前 N 行，展开时渲染全部；内容为空时给占位文案 */
const aiContentHtml = (msg: ChatMessage): string => {
  const content = isFolded(msg) ? foldedContent(msg) : msg.content
  if (content) {
    return renderMarkdown(content)
  }
  return msg.history ? '-' : t('appChat.generating')
}

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
  }
  messages.value.push(userMsg, aiMsg)
  inputMessage.value = ''
  await scrollToBottom()

  const aiIndex = messages.value.length - 1

  eventSource = connectChatSse(app.value.id, messageText, {
    onMessage: (chunk) => {
      const current = messages.value[aiIndex]
      if (!current) {
        return
      }
      current.content += chunk
      current.files = extractFiles(current.content)
      scrollToBottom()
    },
    onDone: () => {
      const current = messages.value[aiIndex]
      if (current) {
        current.done = true
        if (!current.content) {
          current.content = t('appChat.aiThinking')
        }
        current.files = extractFiles(current.content)
      }
      generating.value = false
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
      const current = messages.value[aiIndex]
      if (current && !current.content) {
        current.content = t('appChat.sendFailed')
        current.done = true
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
const toChatMessage = (record: API.ChatHistory): ChatMessage => ({
  id: `history-${record.id ?? ''}`,
  role: record.messageType === 'ai' ? 'ai' : 'user',
  content: record.message ?? '',
  files: record.messageType === 'ai' ? extractFiles(record.message ?? '') : undefined,
  done: true,
  createTime: record.createTime,
  history: true,
  // 超过 10 行的用户历史消息默认折叠
  collapsed:
    record.messageType === 'user' && contentLineCount(record.message ?? '') > FOLD_LINE_COUNT,
})

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
      await scrollToBottom()
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
          <div ref="messageListRef" class="message-list">
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
                  <!-- AI 消息：折叠展示前 10 行 / 生成中纯文本 / 完成后 Markdown 渲染（代码高亮） -->
                  <div
                    v-if="msg.role === 'ai' && isFolded(msg) && !msg.done"
                    class="message-content"
                  >
                    {{ foldedContent(msg) }}
                  </div>
                  <div v-else-if="msg.role === 'ai' && !msg.done" class="message-content">
                    {{ msg.content || t('appChat.generating') }}
                  </div>
                  <div
                    v-else-if="msg.role === 'ai'"
                    class="message-content message-content--md"
                    v-html="aiContentHtml(msg)"
                  ></div>
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
              <template v-if="generating">{{ t('appChat.generating') }}</template>
              <template
                v-else-if="app?.codeGenType === CodeGenTypeEnum.VUE_PROJECT && !previewReady"
              >
                {{ t('appChat.vueBuilding') }}
              </template>
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
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #ececec;
  background: #fafafa;
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
