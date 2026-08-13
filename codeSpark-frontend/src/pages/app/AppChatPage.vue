<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  CloudUploadOutlined,
  PaperClipOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { deployApp, getAppVoById } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { connectChatSse } from '@/utils/sse'
import systemLogo from '@/assets/logo.png'
type ChatRole = 'user' | 'ai'

interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  files?: string[]
  done?: boolean
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
let eventSource: EventSource | null = null

const appId = computed(() => String(route.params.id || ''))

const sameId = (a?: string | number, b?: string | number) => String(a ?? '') === String(b ?? '')

const isOwner = computed(() => {
  return Boolean(app.value?.userId && sameId(app.value.userId, loginUserStore.loginUser.id))
})

const canChat = computed(() => isOwner.value)

const previewUrl = computed(() => {
  if (!app.value?.id || !app.value.codeGenType) {
    return ''
  }
  return `/api/static/${app.value.codeGenType}_${app.value.id}/`
})

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
  }
  const aiMsg: ChatMessage = {
    id: `ai-${Date.now()}`,
    role: 'ai',
    content: '',
    files: [],
    done: false,
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
  if (!appId.value) {
    message.error(t('appChat.loadFailed'))
    loading.value = false
    return
  }
  loading.value = true
  try {
    const res = await getAppVoById({ id: appId.value as unknown as number })
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

onMounted(async () => {
  await fetchApp()
  if (route.query.init === '1' && app.value?.initPrompt && canChat.value) {
    // 创建后跳转：启动名称轮询（等待 AI 异步生成的名称）
    startNamePolling()
    await sendMessage(app.value.initPrompt)
    // 清除 init 参数，避免刷新重复发送
    router.replace({ path: route.path, query: {} })
  } else if (app.value?.codeGenType && app.value.id) {
    // 已有应用时尝试展示预览
    showPreview.value = true
  }
})

// 名称轮询：创建后 appName 是截取的，轮询直到 AI 异步生成新名称
// 最多等待 8 秒，超时则停止并显示当前名称（避免一直转圈）
let namePollTimer: ReturnType<typeof setInterval> | null = null
const NAME_POLL_TIMEOUT = 8

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
  }, 500)
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
    closeSse()
    messages.value = []
    showPreview.value = false
    await fetchApp()
  },
)

onBeforeUnmount(() => {
  closeSse()
  stopNamePolling()
})
</script>

<template>
  <a-spin :spinning="loading" class="chat-spin">
  <div id="appChatPage">
    <header class="chat-header">
      <div class="chat-header__left">
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
        <a-tag
          class="mode-tag"
          :color="app?.codeGenType === 'html' ? 'blue' : 'orange'"
        >
          {{ app?.codeGenType === 'html' ? t('appManage.typeHtml') : t('appManage.typeMultiFile') }}
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
          <a-button
            type="primary"
            :loading="deploying"
            :disabled="!canChat || generating"
          >
            <template #icon><CloudUploadOutlined /></template>
            {{ t('common.deploy') }}
          </a-button>
        </a-popconfirm>
      </a-space>
    </header>

    <div class="chat-body">
      <section class="chat-panel">
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
                v-if="loginUserStore.loginUser.userAvatar"
                class="message-avatar__img"
                :src="loginUserStore.loginUser.userAvatar"
                alt=""
              />
              <span v-else class="message-avatar__fallback">
                {{ loginUserStore.loginUser.userName?.slice(0, 1) || 'U' }}
              </span>
            </div>

            <!-- 昵称 + 气泡 -->
            <div class="message-body">
              <div class="message-nickname">{{ msg.role === 'ai' ? t('appChat.aiName') : loginUserStore.loginUser.userName }}</div>
              <div class="message-bubble" :class="msg.role === 'user' ? 'is-user' : 'is-ai'">
                <div class="message-content">{{ msg.content || t('appChat.generating') }}</div>
                <div v-if="msg.files?.length" class="file-list">
                  <div class="file-list__title">{{ t('appChat.filesGenerated') }}</div>
                  <div v-for="file in msg.files" :key="file" class="file-item">
                    <SettingOutlined />
                    <span>{{ file }}</span>
                  </div>
                </div>
                <div v-if="msg.role === 'ai' && msg.done" class="message-footer">
                  {{ t('appChat.saved') }}
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
            <a-button
              type="primary"
              shape="circle"
              :loading="generating"
              @click="handleSend"
            >
              <template #icon><ArrowUpOutlined /></template>
            </a-button>
          </div>
        </div>
      </section>

      <section class="preview-panel">
        <div class="preview-frame">
          <iframe
            v-if="showPreview && previewUrl"
            :key="previewKey"
            class="preview-iframe"
            :src="previewUrl"
            :title="t('appChat.previewTitle')"
          />
          <div v-else class="preview-empty">
            {{ generating ? t('appChat.generating') : t('appChat.previewEmpty') }}
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

/* 昵称 + 气泡列 */
.message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: 70%;
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
