<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import {
  adminPageUsage,
  adminReset,
  adminResetAll,
} from '@/api/userQuotaUsageController'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatTokens } from '@/utils/formatTokens'

const { t } = useI18n()

// 数据
const data = ref<API.AdminQuotaUsageVO[]>([])
const total = ref(0)

// 分页参数（沿用 PageRequest 惯例）
const searchParams = reactive<API.QuotaUsageQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const columns = computed(() => [
  {
    title: t('adminQuota.id'),
    dataIndex: 'userId',
    width: 176,
  },
  {
    title: t('adminQuota.account'),
    dataIndex: 'userAccount',
    width: 180,
  },
  {
    title: t('adminQuota.usedTokens'),
    dataIndex: 'usedTokens',
    width: 140,
  },
  {
    title: t('adminQuota.remaining'),
    dataIndex: 'remainingTokens',
    width: 180,
  },
  {
    title: t('adminQuota.totalTokens'),
    dataIndex: 'totalTokens',
    width: 140,
  },
  {
    title: t('adminQuota.action'),
    key: 'action',
    width: 110,
  },
])

// 获取数据
const fetchData = async () => {
  const res = await adminPageUsage({ ...searchParams })
  if (res.data.code === 0 && res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } else {
    message.error(t('adminQuota.fetchFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}

// 分页参数
const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalNum: number) => t('adminQuota.totalItems', { total: totalNum }),
}))

// 表格翻页
const doTableChange = (page: TablePaginationConfig) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  fetchData()
}

// 重置单个用户当月额度
const doReset = async (userId: number | undefined) => {
  if (!userId) {
    return
  }
  const res = await adminReset({ userId })
  if (res.data.code === 0) {
    message.success(t('adminQuota.resetSuccess'))
    fetchData()
  } else {
    message.error(t('adminQuota.resetFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}

// 一键重置所有用户（危险操作：需输入关键词确认）
// 关键词固定为 Reset，避免中文/英文环境下误触
const RESET_ALL_KEYWORD = 'Reset'
const resetAllOpen = ref(false)
const resetAllKeyword = ref('')
const resetAllLoading = ref(false)

const openResetAll = () => {
  resetAllKeyword.value = ''
  resetAllOpen.value = true
}

const doResetAll = async () => {
  resetAllLoading.value = true
  try {
    const res = await adminResetAll()
    if (res.data.code === 0) {
      message.success(t('adminQuota.resetSuccess'))
      resetAllOpen.value = false
      fetchData()
    } else {
      message.error(
        t('adminQuota.resetFailed') + getErrorMessage(res.data.code, res.data.message),
      )
    }
  } finally {
    resetAllLoading.value = false
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<template>
  <div id="quotaManagePage">
    <div class="page-toolbar">
      <span class="page-title">{{ t('adminQuota.pageTitle') }}</span>
      <a-button danger @click="openResetAll">{{ t('adminQuota.resetAll') }}</a-button>
    </div>

    <a-table
      class="quota-table"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      size="middle"
      row-key="userId"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userId'">
          <span class="mono-cell">{{ record.userId }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'usedTokens'">
          <span class="mono-cell">{{ formatTokens(record.usedTokens) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'remainingTokens'">
          <!-- -1 = 不限；正常展示 已用/上限 小进度条 -->
          <span v-if="record.remainingTokens === -1" class="muted-cell">
            {{ t('adminQuota.unlimited') }}
          </span>
          <div v-else class="quota-cell">
            <a-progress
              :percent="Math.round(((record.usedTokens ?? 0) / (record.monthlyLimit ?? 1)) * 100)"
              :show-info="false"
              size="small"
              class="quota-bar"
            />
            <span class="mono-cell">
              {{ formatTokens(record.remainingTokens) }} / {{ formatTokens(record.monthlyLimit) }}
            </span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'totalTokens'">
          <span class="mono-cell">{{ formatTokens(record.totalTokens) }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-popconfirm :title="t('adminQuota.resetConfirm')" @confirm="() => doReset(record.userId)">
            <a-button size="small">{{ t('adminQuota.reset') }}</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 全部重置：危险操作，需输入 Reset 才能确认 -->
    <a-modal
      v-model:open="resetAllOpen"
      :title="t('adminQuota.resetAllTitle')"
      :ok-text="t('adminQuota.resetAll')"
      :ok-button-props="{ danger: true, disabled: resetAllKeyword !== RESET_ALL_KEYWORD }"
      :confirm-loading="resetAllLoading"
      @ok="doResetAll"
    >
      <p class="reset-all-tip">{{ t('adminQuota.resetAllConfirm') }}</p>
      <p class="reset-all-hint">
        {{ t('adminQuota.resetAllInputHint', { keyword: RESET_ALL_KEYWORD }) }}
      </p>
      <a-input
        v-model:value="resetAllKeyword"
        :placeholder="RESET_ALL_KEYWORD"
        allow-clear
        @press-enter="() => resetAllKeyword === RESET_ALL_KEYWORD && doResetAll()"
      />
    </a-modal>
  </div>
</template>

<style scoped>
#quotaManagePage {
  overflow: hidden;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
}

.quota-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quota-bar {
  width: 96px;
  margin: 0;
}

.mono-cell {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
  white-space: nowrap;
}

.muted-cell {
  color: rgba(0, 0, 0, 0.45);
}

.reset-all-tip {
  margin: 0 0 8px;
  color: rgba(0, 0, 0, 0.65);
}

.reset-all-hint {
  margin: 0 0 8px;
  color: #ff4d4f;
  font-size: 13px;
}

:deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
  color: rgba(0, 0, 0, 0.88);
  font-weight: 600;
  background: #fafafa;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}
</style>
