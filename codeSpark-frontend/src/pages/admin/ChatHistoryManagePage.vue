<template>
  <div id="chatHistoryManagePage">
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item :label="t('chatHistoryManage.message')">
        <a-input
          v-model:value="searchParams.message"
          :placeholder="t('chatHistoryManage.messagePlaceholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item :label="t('chatHistoryManage.messageType')">
        <a-select
          v-model:value="searchParams.messageType"
          :placeholder="t('chatHistoryManage.messageTypePlaceholder')"
          :options="messageTypeOptions"
          style="width: 160px"
          allow-clear
        />
      </a-form-item>
      <a-form-item :label="t('chatHistoryManage.appId')">
        <a-input
          v-model:value="appIdText"
          :placeholder="t('chatHistoryManage.appIdPlaceholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item :label="t('chatHistoryManage.userId')">
        <a-input
          v-model:value="userIdText"
          :placeholder="t('chatHistoryManage.userIdPlaceholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">{{ t('common.search') }}</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <a-table
      class="chat-history-table"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      size="middle"
      row-key="id"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'id'">
          <span class="mono-cell">{{ record.id }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'message'">
          <a-typography-paragraph
            class="table-text"
            :ellipsis="{ rows: 2, tooltip: true }"
            :content="messagePreview(record.message)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag :color="record.messageType === 'ai' ? 'green' : 'blue'">
            {{
              record.messageType === 'ai'
                ? t('chatHistoryManage.typeAi')
                : t('chatHistoryManage.typeUser')
            }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'appId'">
          <span class="mono-cell">{{ record.appId }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'userId'">
          <span class="mono-cell">{{ record.userId }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          <span class="time-cell">{{ formatDateTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space class="action-space" size="small">
            <a-button size="small" type="link" @click="openChat(record)">
              {{ t('chatHistoryManage.viewChat') }}
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime } from '@/utils/time'

const { t } = useI18n()
const router = useRouter()

const columns = computed(() => [
  { title: 'id', dataIndex: 'id', width: 176 },
  { title: t('chatHistoryManage.message'), dataIndex: 'message' },
  {
    title: t('chatHistoryManage.messageType'),
    dataIndex: 'messageType',
    width: 110,
    align: 'center',
  },
  { title: t('chatHistoryManage.appId'), dataIndex: 'appId', width: 176 },
  { title: t('chatHistoryManage.userId'), dataIndex: 'userId', width: 176 },
  { title: t('chatHistoryManage.createTime'), dataIndex: 'createTime', width: 168 },
  { title: t('chatHistoryManage.action'), key: 'action', width: 108 },
])

const messageTypeOptions = computed(() => [
  { label: t('chatHistoryManage.typeUser'), value: 'user' },
  { label: t('chatHistoryManage.typeAi'), value: 'ai' },
])

const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const appIdText = ref('')
const userIdText = ref('')

const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  message: '',
  messageType: '',
  // 默认按创建时间倒序（PG 驼峰列名需带双引号）
  sortField: '"createTime"',
  sortOrder: 'descend',
})

// 对话内容可能很长（AI 消息为生成的代码），表格只展示压缩后的预览
const MAX_PREVIEW_LENGTH = 200
const messagePreview = (message?: string): string => {
  if (!message) {
    return '-'
  }
  const text = message.replace(/\s+/g, ' ')
  return text.length > MAX_PREVIEW_LENGTH ? `${text.slice(0, MAX_PREVIEW_LENGTH)}...` : text
}

const fetchData = async () => {
  try {
    const payload: API.ChatHistoryQueryRequest = {
      pageNum: searchParams.pageNum,
      pageSize: searchParams.pageSize,
      sortField: searchParams.sortField,
      sortOrder: searchParams.sortOrder,
    }
    if (searchParams.message?.trim()) {
      payload.message = searchParams.message.trim()
    }
    if (searchParams.messageType?.trim()) {
      payload.messageType = searchParams.messageType.trim()
    }
    if (appIdText.value.trim()) {
      // 雪花 ID 以字符串传递，避免 Number 精度丢失
      payload.appId = appIdText.value.trim() as unknown as number
    }
    if (userIdText.value.trim()) {
      payload.userId = userIdText.value.trim() as unknown as number
    }

    const res = await listAllChatHistoryByPageForAdmin(payload)
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error(
        getErrorMessage(res.data.code, res.data.message) || t('chatHistoryManage.fetchFailed'),
      )
    }
  } catch {
    message.error(t('chatHistoryManage.fetchFailed'))
  }
}

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalNum: number) => t('chatHistoryManage.totalItems', { total: totalNum }),
}))

const doTableChange = (page: TablePaginationConfig) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const openChat = (record: API.ChatHistory) => {
  if (record.appId == null) {
    return
  }
  router.push(`/app/chat/${record.appId}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#chatHistoryManagePage {
  overflow: hidden;
}

.chat-history-table {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.table-text {
  max-width: 100%;
  margin-bottom: 0;
  line-height: 1.5;
}

.mono-cell {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
  white-space: nowrap;
}

.time-cell {
  white-space: nowrap;
}

.action-space {
  white-space: nowrap;
}

:deep(.ant-table) {
  font-size: 14px;
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

:deep(.ant-table-tbody > tr:hover > td) {
  background: #f7fbff;
}

:deep(.ant-btn-link) {
  padding-inline: 4px;
}
</style>
