<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item :label="t('userManage.account')">
        <a-input v-model:value="searchParams.userAccount" :placeholder="t('userManage.accountPlaceholder')" />
      </a-form-item>
      <a-form-item :label="t('userManage.userName')">
        <a-input v-model:value="searchParams.userName" :placeholder="t('userManage.userNamePlaceholder')" />
      </a-form-item>
      <a-form-item :label="t('userManage.email')">
        <a-input v-model:value="searchParams.userEmail" :placeholder="t('userManage.emailPlaceholder')" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">{{ t('common.search') }}</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 表格 -->
    <a-table
      class="user-table"
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
        <template v-else-if="column.dataIndex === 'userAvatar'">
          <a-image v-if="record.userAvatar" :src="record.userAvatar" :width="48" :height="48" class="avatar-image" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'userProfile'">
          <a-typography-paragraph
            class="table-text muted-text"
            :ellipsis="{ rows: 2, tooltip: record.userProfile }"
            :content="record.userProfile || '-'"
          />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag v-if="record.userRole === 'admin'" color="green">{{ t('userManage.roleAdmin') }}</a-tag>
          <a-tag v-else color="blue">{{ t('userManage.roleUser') }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          <span class="time-cell">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-popconfirm
            :title="t('userManage.deleteConfirm')"
            @confirm="() => doDelete(record.id)"
          >
            <a-button size="small" danger :disabled="isSelf(record.id)">
              {{ t('common.delete') }}
            </a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { deleteUser, listUserVoByPage } from '@/api/userController'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '@/utils/errorMessage'
import { useLoginUserStore } from '@/stores/loginUser'

const { t } = useI18n()

const loginUserStore = useLoginUserStore()

// 判断是否为当前登录用户（管理员不能删除自己）
const isSelf = (id?: number) => String(id ?? '') === String(loginUserStore.loginUser.id ?? '')

const columns = computed(() => [
  {
    title: 'id',
    dataIndex: 'id',
    width: 176,
  },
  {
    title: t('userManage.account'),
    dataIndex: 'userAccount',
    width: 180,
  },
  {
    title: t('userManage.email'),
    dataIndex: 'userEmail',
    width: 200,
  },
  {
    title: t('userManage.userName'),
    dataIndex: 'userName',
    width: 160,
  },
  {
    title: t('userManage.avatar'),
    dataIndex: 'userAvatar',
    width: 88,
    align: 'center',
  },
  {
    title: t('userManage.profile'),
    dataIndex: 'userProfile',
  },
  {
    title: t('userManage.userRole'),
    dataIndex: 'userRole',
    width: 110,
  },
  {
    title: t('userManage.createTime'),
    dataIndex: 'createTime',
    width: 168,
  },
  {
    title: t('userManage.action'),
    key: 'action',
    width: 96,
  },
])

// 数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } else {
    message.error(t('userManage.fetchFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}

// 分页参数
const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalNum: number) => t('userManage.totalItems', { total: totalNum }),
}))

// 表格变化处理
const doTableChange = (page: TablePaginationConfig) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  fetchData()
}

// 搜索
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

// 删除
const doDelete = async (id: number | undefined) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success(t('userManage.deleteSuccess'))
    fetchData()
  } else {
    message.error(t('userManage.deleteFailed'))
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  overflow: hidden;
}

.user-table {
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

.avatar-image {
  object-fit: cover;
  border-radius: 50%;
  background: #f5f5f5;
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
</style>
