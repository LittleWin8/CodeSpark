<template>
  <div id="appManagePage">
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item :label="t('appManage.appName')">
        <a-input
          v-model:value="searchParams.appName"
          :placeholder="t('appManage.appNamePlaceholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item :label="t('appManage.userId')">
        <a-input
          v-model:value="userIdText"
          :placeholder="t('appManage.userIdPlaceholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item :label="t('appManage.codeGenType')">
        <a-select
          v-model:value="searchParams.codeGenType"
          :placeholder="t('appManage.codeGenTypePlaceholder')"
          :options="codeGenTypeOptions"
          style="width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">{{ t('common.search') }}</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <a-table
      class="app-table"
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
        <template v-else-if="column.dataIndex === 'appName'">
          <a-typography-paragraph
            class="table-text"
            :ellipsis="{ rows: 2, tooltip: record.appName }"
            :content="record.appName || '-'"
          />
        </template>
        <template v-else-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="64" :height="40" class="cover-image" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'initPrompt'">
          <a-typography-paragraph
            class="table-text muted-text"
            :ellipsis="{ rows: 2, tooltip: true }"
            :content="record.initPrompt"
          />
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          <a-tag color="blue">{{ codeGenTypeLabel(record.codeGenType) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag :color="record.priority === GOOD_APP_PRIORITY ? 'green' : 'default'">
            {{ record.priority ?? 0 }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'userId'">
          <span class="mono-cell">{{ record.userId }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          <span class="time-cell">{{ formatDateTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space class="action-space" size="small">
            <!-- 使用原生链接新开页，避免 window.open 被浏览器拦截 -->
            <a-button size="small" type="link" @click="openEditDrawer(record)">
              {{ t('common.edit') }}
            </a-button>
            <a-button
              size="small"
              type="link"
              class="feature-btn"
              :loading="featuringId === record.id"
              @click="doFeature(record)"
            >
              {{ record.priority === GOOD_APP_PRIORITY ? t('appManage.unfeature') : t('common.feature') }}
            </a-button>
            <a-popconfirm :title="t('appManage.deleteConfirm')" @confirm="doDelete(record.id)">
              <a-button size="small" type="link" danger :loading="deletingId === record.id">
                {{ t('common.delete') }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer
      v-model:open="editDrawerOpen"
      :title="t('appEdit.title')"
      width="480"
      :destroy-on-close="true"
      @close="resetEditForm"
    >
      <a-spin :spinning="editLoading">
        <a-form class="edit-form" :model="editForm" layout="vertical" @finish="doUpdate">
          <a-form-item
            :label="t('appEdit.appName')"
            name="appName"
            :rules="[{ required: true, message: t('appEdit.appNameRequired') }]"
          >
            <a-input
              v-model:value="editForm.appName"
              :placeholder="t('appEdit.appNamePlaceholder')"
              allow-clear
            />
          </a-form-item>

          <a-form-item :label="t('appEdit.cover')" name="cover">
            <a-input
              v-model:value="editForm.cover"
              :placeholder="t('appEdit.coverPlaceholder')"
              allow-clear
            />
          </a-form-item>

          <a-form-item :label="t('appEdit.priority')" name="priority">
            <a-input-number
              v-model:value="editForm.priority"
              :placeholder="t('appEdit.priorityPlaceholder')"
              style="width: 100%"
            />
          </a-form-item>

          <div class="drawer-footer">
            <a-space>
              <a-button @click="editDrawerOpen = false">{{ t('appEdit.back') }}</a-button>
              <a-button type="primary" html-type="submit" :loading="editSaving">
                {{ t('common.save') }}
              </a-button>
            </a-space>
          </div>
        </a-form>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import {
  deleteAppByAdmin,
  getAppVoByIdByAdmin,
  listAppVoByPageByAdmin,
  updateAppByAdmin,
} from '@/api/appController'
import { useCodeGenType } from '@/constants/codeGenType'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime } from '@/utils/time'

const { t } = useI18n()
const { options: codeGenTypeOptions, label: codeGenTypeLabel } = useCodeGenType()

const GOOD_APP_PRIORITY = 99

const columns = computed(() => [
  { title: 'id', dataIndex: 'id', width: 176 },
  { title: t('appManage.appName'), dataIndex: 'appName', width: 150 },
  { title: t('appManage.cover'), dataIndex: 'cover', width: 84, align: 'center' },
  { title: t('appManage.initPrompt'), dataIndex: 'initPrompt' },
  { title: t('appManage.codeGenType'), dataIndex: 'codeGenType', width: 118 },
  { title: t('appManage.priority'), dataIndex: 'priority', width: 78, align: 'center' },
  { title: t('appManage.userId'), dataIndex: 'userId', width: 176 },
  { title: t('appManage.createTime'), dataIndex: 'createTime', width: 168 },
  { title: t('appManage.action'), key: 'action', width: 148 },
])

const data = ref<API.AppVO[]>([])
const total = ref(0)
const userIdText = ref('')
const featuringId = ref<string | number | null>(null)
const deletingId = ref<string | number | null>(null)
const editDrawerOpen = ref(false)
const editLoading = ref(false)
const editSaving = ref(false)

const editForm = reactive<API.AppAdminUpdateRequest>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
})

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  appName: '',
  codeGenType: '',
  // 默认按创建时间倒序（PG 驼峰列名需带双引号）
  sortField: '"createTime"',
  sortOrder: 'descend',
})

const fetchData = async () => {
  try {
    const payload: API.AppQueryRequest = {
      pageNum: searchParams.pageNum,
      pageSize: searchParams.pageSize,
      sortField: searchParams.sortField,
      sortOrder: searchParams.sortOrder,
    }
    if (searchParams.appName?.trim()) {
      payload.appName = searchParams.appName.trim()
    }
    if (searchParams.codeGenType?.trim()) {
      payload.codeGenType = searchParams.codeGenType.trim()
    }
    if (userIdText.value.trim()) {
      // 雪花 ID 以字符串传递，避免 Number 精度丢失
      payload.userId = userIdText.value.trim() as unknown as number
    }

    const res = await listAppVoByPageByAdmin(payload)
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appManage.fetchFailed'))
    }
  } catch {
    message.error(t('appManage.fetchFailed'))
  }
}

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalNum: number) => t('appManage.totalItems', { total: totalNum }),
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

const resetEditForm = () => {
  editForm.id = undefined
  editForm.appName = ''
  editForm.cover = ''
  editForm.priority = 0
}

const openEditDrawer = async (record: API.AppVO) => {
  if (record.id == null) {
    return
  }
  editDrawerOpen.value = true
  editLoading.value = true
  resetEditForm()
  try {
    const res = await getAppVoByIdByAdmin({ id: record.id })
    if (res.data.code === 0 && res.data.data) {
      const app = res.data.data
      editForm.id = app.id
      editForm.appName = app.appName ?? ''
      editForm.cover = app.cover ?? ''
      editForm.priority = app.priority ?? 0
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appEdit.loadFailed'))
      editDrawerOpen.value = false
    }
  } catch {
    message.error(t('appEdit.loadFailed'))
    editDrawerOpen.value = false
  } finally {
    editLoading.value = false
  }
}

const doUpdate = async () => {
  if (editForm.id == null) {
    return
  }
  editSaving.value = true
  try {
    const res = await updateAppByAdmin({
      id: editForm.id,
      appName: editForm.appName,
      cover: editForm.cover,
      priority: editForm.priority,
    })
    if (res.data.code === 0) {
      message.success(t('appEdit.saveSuccess'))
      editDrawerOpen.value = false
      await fetchData()
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appEdit.saveFailed'))
    }
  } catch {
    message.error(t('appEdit.saveFailed'))
  } finally {
    editSaving.value = false
  }
}

const doFeature = async (record: API.AppVO) => {
  if (record.id == null || record.id === ('' as unknown as number)) {
    return
  }
  const isFeatured = record.priority === GOOD_APP_PRIORITY
  featuringId.value = record.id
  try {
    const res = await updateAppByAdmin({
      id: record.id,
      appName: record.appName,
      cover: record.cover,
      priority: isFeatured ? 0 : GOOD_APP_PRIORITY,
    })
    if (res.data.code === 0) {
      message.success(t(isFeatured ? 'appManage.unfeatureSuccess' : 'appManage.featureSuccess'))
      await fetchData()
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appManage.featureFailed'))
    }
  } catch {
    message.error(t('appManage.featureFailed'))
  } finally {
    featuringId.value = null
  }
}

const doDelete = async (id?: number | string) => {
  if (id == null || id === '') {
    return
  }
  deletingId.value = id
  try {
    const res = await deleteAppByAdmin({ id: id as number })
    if (res.data.code === 0) {
      message.success(t('appManage.deleteSuccess'))
      await fetchData()
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appManage.deleteFailed'))
    }
  } catch {
    message.error(t('appManage.deleteFailed'))
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#appManagePage {
  overflow: hidden;
}

.app-table {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.table-text {
  max-width: 100%;
  margin-bottom: 0;
  line-height: 1.5;
}

.muted-text {
  color: rgba(0, 0, 0, 0.65);
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

.cover-image {
  object-fit: cover;
  border-radius: 6px;
  background: #f5f5f5;
}

.action-space {
  white-space: nowrap;
}

.edit-form {
  min-height: calc(100vh - 112px);
  padding-bottom: 72px;
  position: relative;
}

.drawer-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding-top: 16px;
  text-align: right;
  background: #fff;
  border-top: 1px solid #f0f0f0;
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

.feature-btn {
  min-width: 56px;
  text-align: center;
}
</style>
