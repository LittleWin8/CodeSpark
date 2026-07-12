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
      <a-form-item>
        <a-button type="primary" html-type="submit">{{ t('common.search') }}</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 表格 -->
    <a-table :columns="columns" :data-source="data" :pagination="pagination" @change="doTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="120" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag v-if="record.userRole === 'admin'" color="green">{{ t('userManage.roleAdmin') }}</a-tag>
          <a-tag v-else color="blue">{{ t('userManage.roleUser') }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button danger @click="() => doDelete(record.id)">{{ t('common.delete') }}</a-button>
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

const { t } = useI18n()

const columns = computed(() => [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: t('userManage.account'),
    dataIndex: 'userAccount',
  },
  {
    title: t('userManage.userName'),
    dataIndex: 'userName',
  },
  {
    title: t('userManage.avatar'),
    dataIndex: 'userAvatar',
  },
  {
    title: t('userManage.profile'),
    dataIndex: 'userProfile',
  },
  {
    title: t('userManage.userRole'),
    dataIndex: 'userRole',
  },
  {
    title: t('userManage.createTime'),
    dataIndex: 'createTime',
  },
  {
    title: t('userManage.action'),
    key: 'action',
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
    message.error(t('userManage.fetchFailed') + res.data.message)
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
