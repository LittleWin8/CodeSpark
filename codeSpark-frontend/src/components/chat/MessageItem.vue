<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- msg 是父子共享的可变状态对象：父组件（AppChatPage）的流式
   回调（onThinking/onMessage/onToolExecuted）直接修改其字段（thinking/content/blocks/planningNext…），
   子组件仅负责折叠/展开、工具卡片展开等展示交互的原地修改，属同一共享状态容器，非单向 props 场景 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircleFilled,
  DownOutlined,
  LoadingOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import type { ChatMessage, MessageBlock, TextBlock } from '@/types/chat'
import { renderMarkdown } from '@/utils/markdown'
import systemLogo from '@/assets/logo.png'

const props = defineProps<{
  msg: ChatMessage
  /** 当前登录/展示用户昵称（用户消息展示用） */
  userName?: string
  /** 当前登录/展示用户头像 URL（用户消息展示用） */
  userAvatar?: string
  /** 思考读秒的"当前时刻"（由父组件思考期间每秒驱动；停止后不再变化） */
  nowTick?: number
}>()

const { t } = useI18n()

/** 思考耗时（秒）：推理结束时冻结（thinkingElapsed），进行中按时间戳实时计算 */
const thinkingSeconds = (msg: ChatMessage) =>
  msg.thinkingElapsed ??
  (msg.thinkingStart ? Math.max(1, Math.round((Date.now() - msg.thinkingStart) / 1000)) : 0)

/** 思考实时读秒（由父组件 nowTick 驱动，思考期间每秒 +1） */
const liveSeconds = computed(() =>
  props.msg.thinkingStart
    ? Math.max(1, Math.round(((props.nowTick ?? Date.now()) - props.msg.thinkingStart) / 1000))
    : 0,
)

// 内容超过该行数才显示收起按钮，收起时展示前 N 行内容
const FOLD_LINE_COUNT = 10

/** 超过该字符数的文本块，Markdown 渲染改为空闲时间异步执行（避免 highlight.js 高亮大代码块阻塞主线程） */
const ASYNC_RENDER_THRESHOLD = 20000

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

/** 折叠视图的 Markdown HTML（前 FOLD_LINE_COUNT 行摘要）：与展开样式一致，收起也能看到格式化内容 */
const foldedHtml = computed(() => {
  if (!isFolded(props.msg)) {
    return ''
  }
  const text = foldedContent(props.msg)
  return text ? renderMarkdown(text) : ''
})

// ---- 文本块 Markdown 懒渲染 ----
// 每个文本块渲染一次并缓存；短文本同步渲染（无感），大段代码（如整个 HTML/多文件内容）
// 交给 requestIdleCallback（降级 setTimeout）在空闲时渲染，done 后不再卡主线程。
const renderedHtml = ref<string[]>([])

const scheduleRender = () => {
  if (!props.msg.done) {
    return
  }
  blocksOf(props.msg).forEach((block, i) => {
    if (block.type !== 'text' || renderedHtml.value[i] !== undefined) {
      return
    }
    const text = block.text
    if (!text) {
      renderedHtml.value[i] = '-'
      return
    }
    if (text.length <= ASYNC_RENDER_THRESHOLD) {
      renderedHtml.value[i] = renderMarkdown(text)
    } else {
      const idle =
        (window as unknown as { requestIdleCallback?: (cb: () => void) => number })
          .requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 50))
      idle(() => {
        if (renderedHtml.value[i] === undefined) {
          renderedHtml.value[i] = renderMarkdown(text)
        }
      })
    }
  })
}

// done 后（实时消息流结束 / 历史消息挂载）统一调度一次渲染
watch(
  () => props.msg.done,
  (done) => {
    if (done) {
      scheduleRender()
    }
  },
  { immediate: true },
)
</script>

<template>
  <div
    class="message-row"
    :class="msg.role === 'user' ? 'message-row--user' : 'message-row--ai'"
  >
    <!-- 头像（左侧：AI logo；右侧：用户头像） -->
    <div class="message-avatar">
      <img
        v-if="msg.role === 'ai'"
        class="message-avatar__img"
        :src="systemLogo"
        alt="CodeSpark AI"
      />
      <template v-else>
        <img v-if="userAvatar" class="message-avatar__img" :src="userAvatar" alt="" />
        <span v-else class="message-avatar__fallback">{{ userName?.slice(0, 1) || 'U' }}</span>
      </template>
    </div>

    <!-- 昵称 + 气泡 -->
    <div class="message-body">
      <div class="message-nickname">
        {{ msg.role === 'ai' ? t('appChat.aiName') : userName }}
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
              {{ t('appChat.thinkingLive', { sec: liveSeconds }) }}
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

        <!-- AI 消息：按块渲染（文本 + 文件写入卡片）；折叠时展示前 N 行文本（同样 Markdown 渲染）与文件摘要 -->
        <template v-if="msg.role === 'ai'">
          <template v-if="isFolded(msg)">
            <div class="message-content message-content--md">
              <span class="msg-rendered" v-html="foldedHtml"></span>
            </div>
            <div v-if="toolCardCount(msg)" class="message-fold-hint">
              {{ t('appChat.filesGenerated') }}（{{ toolCardCount(msg) }}）
            </div>
          </template>
          <template v-else>
            <template v-for="(block, bi) in blocksOf(msg)" :key="bi">
              <div v-if="block.type === 'text'" class="message-content message-content--md">
                <!--
                  流式期间渲染纯文本（Vue 插值自动转义），避免每个分片都对整段代码重复做
                  markdown 解析 + highlight.js 高亮（非 VUE 模式的大段代码会导致 O(n²) 卡顿）；
                  done 后统一渲染一次：短文本同步，大段代码异步（requestIdleCallback），
                  渲染完成前仍显示纯文本，完成后淡入切换。
                -->
                <template v-if="block.text">
                  <span v-if="msg.done" class="msg-rendered" v-html="renderedHtml[bi] ?? block.text"></span>
                  <span v-else class="message-stream">{{ block.text }}</span>
                </template>
                <template v-else>{{ bi === 0 ? t('appChat.generating') : '' }}</template>
              </div>
              <div v-else class="tool-card">
                <div class="tool-card__head">
                  <LoadingOutlined v-if="block.writing" spin class="tool-card__icon tool-card__icon--writing" />
                  <CheckCircleFilled v-else class="tool-card__icon tool-card__icon--done" />
                  <span class="tool-card__path">{{ block.path }}</span>
                  <span class="tool-card__status" :class="block.writing ? 'is-writing' : 'is-done'">
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
          v-if="msg.role === 'ai' && ((msg.done && !msg.history) || shouldShowFoldBtn(msg))"
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
</template>

<style scoped>
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

/* 流式期间的纯文本渲染：保留换行与空白，避免每个分片重跑 Markdown/高亮导致卡顿 */
.message-stream {
  white-space: pre-wrap;
  word-break: break-word;
}

/* 生成完成后由纯文本切换到 Markdown 渲染时，做一次淡入过渡，避免"突然变脸" */
.msg-rendered {
  animation: msg-render-in 0.3s ease;
}

@keyframes msg-render-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
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
</style>
