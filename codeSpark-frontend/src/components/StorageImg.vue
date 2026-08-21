<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { extractOssKeyFromUrl, isStorageKey, resolveFileUrl } from '@/utils/storage'
import { UserOutlined } from '@ant-design/icons-vue'

/**
 * 存储图片组件：统一处理「存储标识/预签名 URL → 可访问图片」的展示与兜底。
 * - 传入存储标识（oss: / local:）→ 先解析为可访问 URL 再显示；
 * - 预签名 URL 加载失败（403 过期等）→ 自动从 URL 提取 oss key 重新兑换（仅重试一次）→ 再失败显示占位；
 * - 本地静态地址 / 外链 → 直接显示。
 */
const props = defineProps<{
  src?: string | null
  alt?: string
}>()

const emit = defineEmits<{ (e: 'error'): void }>()

// 实际展示的 src
const displaySrc = ref('')
// 是否已重试过一次（防 403 死循环）
const retried = ref(false)
// 解析失败/加载失败后是否显示占位
const failed = ref(false)

// 直接可用（本地地址/外链）的原值，用于兜底判断
const rawUsable = computed(() => {
  const s = props.src ?? ''
  return s.startsWith('/') || /^https?:\/\//i.test(s)
})

const showPlaceholder = computed(() => failed.value || (!displaySrc.value && !rawUsable.value))

// 加载/解析入口
async function applySrc(raw: string | null | undefined) {
  const value = raw ?? ''
  retried.value = false
  failed.value = false
  if (!value) {
    displaySrc.value = ''
    return
  }
  if (isStorageKey(value)) {
    // 存储标识：先解析为 URL
    const url = await resolveFileUrl(value)
    displaySrc.value = url
    if (!url) {
      failed.value = true
    }
  } else {
    // URL（本地路径 / 预签名 / 外链）直接显示
    displaySrc.value = value
  }
}

function handleImgError() {
  if (retried.value) {
    // 已重试过一次，放弃并显示占位
    failed.value = true
    emit('error')
    return
  }
  // 第一次失败：尝试识别为 OSS 预签名 URL 过期，兑换新票重试一次
  retried.value = true
  const ossKey = extractOssKeyFromUrl(displaySrc.value)
  if (ossKey) {
    resolveFileUrl(ossKey).then((url) => {
      if (url) {
        displaySrc.value = url
      } else {
        failed.value = true
        emit('error')
      }
    })
  } else {
    // 本地文件/外链失败：多为文件已删除，直接占位
    failed.value = true
    emit('error')
  }
}

watch(
  () => props.src,
  (val) => {
    applySrc(val)
  },
  { immediate: true },
)
</script>

<template>
  <span class="storage-img">
    <img
      v-if="displaySrc && !showPlaceholder"
      :src="displaySrc"
      :alt="alt"
      @error="handleImgError"
    />
    <span v-else class="storage-img__placeholder">
      <UserOutlined v-if="!alt" />
      <template v-else>{{ (alt || '?').slice(0, 1) }}</template>
    </span>
  </span>
</template>

<style scoped>
.storage-img {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.storage-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.storage-img__placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: #f0f0f0;
  color: #999;
  font-size: 18px;
}
</style>