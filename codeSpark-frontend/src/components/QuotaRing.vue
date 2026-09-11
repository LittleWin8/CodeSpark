<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuotaStore } from '@/stores/quota'
import { formatTokens } from '@/utils/formatTokens'

const { t } = useI18n()
const quotaStore = useQuotaStore()

// C 形环尺寸与几何参数
const SIZE = 36
const RADIUS = 15
const STROKE = 4
const CIRCUMFERENCE = 2 * Math.PI * RADIUS
// 固定开口角度：右侧 90° 开口（100% 时仍是 C 形，缺口不会消失）
const OPENING_DEG = 90
const GAP_LENGTH = (CIRCUMFERENCE * OPENING_DEG) / 360
// 弧起点相对路径 0 点（3 点钟）的偏移：让缺口在右侧居中
const ARC_START_SHIFT = GAP_LENGTH / 2
// C 形管道全长（彩色弧最大长度）
const TRACK_LENGTH = CIRCUMFERENCE - GAP_LENGTH

// 是否占位态：数据未加载 / 不限额 / 功能关闭 → 灰管道 + ∞
const unlimitedDisplay = computed(
  () => !quotaStore.info || Boolean(quotaStore.info.unlimited) || !quotaStore.info.enabled,
)

// 颜色：正常=主题绿，警告=橙，危险=红，占位=灰
const strokeColor = computed(() => {
  if (unlimitedDisplay.value) {
    return '#b6c2c1'
  }
  switch (quotaStore.level) {
    case 'danger':
      return '#ff4d4f'
    case 'warning':
      return '#faad14'
    default:
      return '#1f8f7a'
  }
})

// 环内文字：从圆心起向右穿过开口延伸
const ringText = computed(() =>
  unlimitedDisplay.value ? '∞' : `${quotaStore.remainingPercent}%`,
)

// 剩余弧长：占 C 形管道全长的比例
const fillLength = computed(() => {
  const percent = unlimitedDisplay.value ? 100 : quotaStore.remainingPercent
  const len = (TRACK_LENGTH * percent) / 100
  return len > 0 && len < 1 ? 1 : len
})

// dasharray：第二段给足周长，保证整圆内只出现一段弧
const trackDashArray = `${TRACK_LENGTH} ${CIRCUMFERENCE}`
const trackDashOffset = TRACK_LENGTH + CIRCUMFERENCE - ARC_START_SHIFT
const fillDashArray = computed(() => `${fillLength.value} ${CIRCUMFERENCE}`)
const fillDashOffset = computed(() => fillLength.value + CIRCUMFERENCE - ARC_START_SHIFT)

// 卡片内已用进度条百分比
const usedPercent = computed(() => {
  const q = quotaStore.info
  if (!q?.monthlyLimit || q.monthlyLimit <= 0) {
    return 0
  }
  return Math.min(100, Math.round(((q.usedTokens ?? 0) / q.monthlyLimit) * 100))
})
</script>

<template>
  <div v-if="quotaStore.visible" class="quota-ring-wrap">
    <a-popover placement="bottom" trigger="click">
      <template #content>
        <div class="quota-card">
          <div class="quota-card-title">{{ t('quota.cardTitle') }}</div>
          <!-- 数据未加载：占位提示，避免空白卡片 -->
          <div v-if="!quotaStore.info" class="quota-loading">{{ t('quota.loading') }}</div>
          <template v-else>
            <a-progress
              :percent="usedPercent"
              :show-info="false"
              :stroke-color="strokeColor"
              size="small"
            />
            <div class="quota-row">
              <span>{{ t('quota.used') }}</span>
              <span class="quota-num">
                {{ formatTokens(quotaStore.info?.usedTokens) }} /
                {{ formatTokens(quotaStore.info?.monthlyLimit) }}
              </span>
            </div>
            <div class="quota-row">
              <span>{{ t('quota.remaining') }}</span>
              <span class="quota-num">
                {{ formatTokens(quotaStore.info?.remainingTokens) }}
              </span>
            </div>
            <div class="quota-note">{{ t('quota.resetNote') }}</div>
          </template>
        </div>
      </template>

      <!-- C 形环：灰色管道 = 全部额度，彩色弧 = 剩余额度，右侧固定开口 -->
      <div class="quota-c-ring" :style="{ width: SIZE + 'px', height: SIZE + 'px' }">
        <svg class="quota-c-svg" :width="SIZE" :height="SIZE" :viewBox="`0 0 ${SIZE} ${SIZE}`">
          <!-- 管道背景（始终是整个 C 形） -->
          <circle
            :cx="SIZE / 2"
            :cy="SIZE / 2"
            :r="RADIUS"
            fill="none"
            stroke="#e8edec"
            :stroke-width="STROKE"
            stroke-linecap="round"
            :stroke-dasharray="trackDashArray"
            :stroke-dashoffset="trackDashOffset"
          />
          <!-- 剩余额度弧：消耗后从一端退去，露出管道背景 -->
          <circle
            class="quota-c-arc"
            :cx="SIZE / 2"
            :cy="SIZE / 2"
            :r="RADIUS"
            fill="none"
            :stroke="strokeColor"
            :stroke-width="STROKE"
            stroke-linecap="round"
            :stroke-dasharray="fillDashArray"
            :stroke-dashoffset="fillDashOffset"
          />
        </svg>
        <span class="quota-c-text" :style="{ color: strokeColor }">{{ ringText }}</span>
      </div>
    </a-popover>
  </div>
</template>

<style scoped>
.quota-ring-wrap {
  display: flex;
  align-items: center;
  cursor: pointer;
}

/* 容器只占环的宽度，右侧文字溢出时靠 margin 预留出空间，避免贴住头像 */
.quota-c-ring {
  position: relative;
  flex-shrink: 0;
  margin-right: 12px;
}

.quota-c-svg {
  display: block;
}

.quota-c-arc {
  transition:
    stroke-dasharray 0.4s ease,
    stroke-dashoffset 0.4s ease,
    stroke 0.3s ease;
}

/* 文字从圆心起向右延伸（与昵称同字号） */
.quota-c-text {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.quota-card {
  width: 200px;
}

.quota-card-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.quota-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  padding: 4px 0;
}

.quota-num {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
  color: #1d1d1f;
}

.quota-note {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.quota-loading {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
  padding: 6px 0;
}
</style>
