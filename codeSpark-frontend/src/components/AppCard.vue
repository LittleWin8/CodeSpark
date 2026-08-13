<template>
  <div class="app-card" @click="emit('click')">
    <div class="app-card__cover">
      <!-- 有封面：显示封面；无封面：picsum 兜底；加载失败：占位符 -->
      <img
        v-if="coverSrc && !coverError"
        :src="coverSrc"
        :alt="app.appName"
        @error="coverError = true"
      />
      <div v-else class="app-card__cover-placeholder">
        {{ app.appName?.slice(0, 1) || 'A' }}
      </div>
    </div>

    <div v-if="variant === 'mine'" class="app-card__body">
      <div class="app-card__name">{{ app.appName }}</div>
      <div class="app-card__meta">
        {{ t('home.createdAt', { time: relativeTime }) }}
      </div>
    </div>

    <div v-else class="app-card__body app-card__body--featured">
      <a-avatar :src="app.user?.userAvatar" :size="36">
        {{ app.user?.userName?.slice(0, 1) || 'U' }}
      </a-avatar>
      <div class="app-card__info">
        <div class="app-card__name">{{ app.appName }}</div>
        <div class="app-card__meta">
          {{ app.user?.userName || t('home.official') }}
        </div>
      </div>
      <div class="app-card__tags">
        <a-tag :color="codeGenTagColor">{{ codeGenTagLabel }}</a-tag>
      </div>
    </div>

    <!-- hover 操作层 -->
    <div class="app-card__overlay">
      <div class="app-card__actions">
        <!-- 我的作品 -->
        <template v-if="variant === 'mine'">
          <a-button size="small" type="primary" @click="handleViewChat">
            <template #icon><MessageOutlined /></template>
            {{ t('common.viewChat') }}
          </a-button>
          <a-button v-if="isDeployed" size="small" @click="handleViewWork">
            <template #icon><EyeOutlined /></template>
            {{ t('common.viewWork') }}
          </a-button>
        </template>
        <!-- 精选案例：悬停展示预览 -->
        <template v-else>
          <a-button size="small" type="primary" @click="handlePreview">
            <template #icon><EyeOutlined /></template>
            {{ t('common.preview') }}
          </a-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { EyeOutlined, MessageOutlined } from '@ant-design/icons-vue'
import { useCodeGenType } from '@/constants/codeGenType'
import { formatRelativeTime } from '@/utils/time'

const props = withDefaults(
  defineProps<{
    app: API.AppVO
    variant?: 'mine' | 'featured'
  }>(),
  {
    variant: 'mine',
  },
)

const emit = defineEmits<{
  click: []
  viewChat: []
  viewWork: []
  preview: []
}>()

const { t, locale } = useI18n()
const { label: codeGenTypeLabel, color: codeGenTypeColor } = useCodeGenType()

const isDeployed = computed(() => Boolean(props.app.deployKey))

// 封面图片加载失败时回退占位符
const coverError = ref(false)

// 封面：优先用户上传的 cover；无 cover 用 picsum（按应用名生成稳定图）
const coverSrc = computed(() => {
  if (props.app.cover) {
    return props.app.cover
  }
  const seed = encodeURIComponent(props.app.appName || 'app')
  return `https://picsum.photos/seed/${seed}/400/300`
})

const relativeTime = computed(() => formatRelativeTime(props.app.createTime, locale.value))

const codeGenTagLabel = computed(() => codeGenTypeLabel(props.app.codeGenType))

const codeGenTagColor = computed(() => codeGenTypeColor(props.app.codeGenType))

const handleViewChat = (e: MouseEvent) => {
  e.stopPropagation()
  emit('viewChat')
}

const handleViewWork = (e: MouseEvent) => {
  e.stopPropagation()
  emit('viewWork')
}

const handlePreview = (e: MouseEvent) => {
  e.stopPropagation()
  emit('preview')
}
</script>

<style scoped>
.app-card {
  cursor: pointer;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  position: relative;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}

.app-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
}

.app-card__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(2px);
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
  z-index: 2;
}

.app-card:hover .app-card__overlay {
  opacity: 1;
  pointer-events: auto;
}

.app-card__actions {
  display: flex;
  gap: 10px;
  padding: 8px;
}

.app-card__cover {
  aspect-ratio: 16 / 10;
  background: #eef2f5;
  overflow: hidden;
}

.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.app-card__cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 700;
  color: #7aa8b8;
  background: linear-gradient(135deg, #dff3f5 0%, #f4fafa 100%);
}

.app-card__body {
  padding: 16px 18px 18px;
}

.app-card__body--featured {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-card__info {
  flex: 1;
  min-width: 0;
}

.app-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-card__meta {
  margin-top: 4px;
  font-size: 13px;
  color: #86868b;
}

.app-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
  max-width: 42%;
}
</style>
