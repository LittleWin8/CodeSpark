<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { deleteApp, getAppVoById, updateApp } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime } from '@/utils/time'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const saving = ref(false)
const appId = computed(() => String(route.params.id || ''))

// 完整的应用详情（用于只读展示）
const appDetail = ref<API.AppVO>()

const formState = reactive({
  appName: '',
})

const sameId = (a?: string | number, b?: string | number) => String(a ?? '') === String(b ?? '')

// 部署访问地址：已部署返回完整 URL，未部署返回空
const accessUrl = computed(() => {
  if (!appDetail.value?.deployKey) {
    return ''
  }
  return `http://localhost/${appDetail.value.deployKey}/`
})

const fetchApp = async () => {
  if (!appId.value) {
    message.error(t('appEdit.loadFailed'))
    return
  }
  loading.value = true
  try {
    const idParam = appId.value as unknown as number
    const res = await getAppVoById({ id: idParam })

    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      appDetail.value = data
      // 仅本人可编辑
      if (!sameId(data.userId, loginUserStore.loginUser.id)) {
        message.error(t('error.noAuth'))
        await router.replace('/noAuth')
        return
      }
      formState.appName = data.appName ?? ''
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appEdit.loadFailed'))
    }
  } finally {
    loading.value = false
  }
}

const handleDelete = async () => {
  const res = await deleteApp({ id: appId.value as unknown as number })
  if (res.data.code === 0) {
    message.success(t('appManage.deleteSuccess'))
    await router.push('/')
  } else {
    message.error(getErrorMessage(res.data.code, res.data.message) || t('appManage.deleteFailed'))
  }
}

const handleSubmit = async () => {
  saving.value = true
  try {
    const idParam = appId.value as unknown as number
    const res = await updateApp({
      id: idParam,
      appName: formState.appName,
    })

    if (res.data.code === 0) {
      message.success(t('appEdit.saveSuccess'))
      // 保存成功后跳转回对话页面
      await router.push(`/app/chat/${idParam}`)
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appEdit.saveFailed'))
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchApp()
})
</script>

<template>
  <div id="appEditPage">
    <h2 class="title">{{ t('appEdit.title') }}</h2>
    <a-spin :spinning="loading">
      <!-- 上方：编辑表单 + 操作按钮 -->
      <div class="app-edit-form">
        <a-form
          :model="formState"
          layout="vertical"
          @finish="handleSubmit"
        >
          <a-form-item
            :label="t('appEdit.appName')"
            name="appName"
            :rules="[{ required: true, message: t('appEdit.appNameRequired') }]"
          >
            <a-input
              v-model:value="formState.appName"
              :placeholder="t('appEdit.appNamePlaceholder')"
            />
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" html-type="submit" :loading="saving">
                {{ t('common.save') }}
              </a-button>
              <a-button @click="router.back()">{{ t('appEdit.back') }}</a-button>
              <a-popconfirm
                :title="t('appManage.deleteConfirm')"
                @confirm="handleDelete"
              >
                <a-button danger>{{ t('common.delete') }}</a-button>
              </a-popconfirm>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <!-- 下方：应用详情（两列只读） -->
      <div v-if="appDetail" class="app-detail">
        <h3 class="detail-title">{{ t('appEdit.detail') }}</h3>
        <a-descriptions :column="2" size="default" bordered>
          <a-descriptions-item :label="t('appEdit.appId')">
            <span class="mono-cell">{{ appDetail.id }}</span>
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.codeGenType')">
            <a-tag color="blue">
              {{ appDetail.codeGenType === 'multi_file' ? t('appEdit.tagMultiFile') : t('appEdit.tagHtml') }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.creator')">
            {{ appDetail.user?.userName || appDetail.userId }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.deployKey')">
            <template v-if="appDetail.deployKey">
              <a-tag color="green">{{ t('appEdit.deployed') }}</a-tag>
              <span class="mono-cell">{{ appDetail.deployKey }}</span>
            </template>
            <template v-else>
              <a-tag>{{ t('appEdit.notDeployed') }}</a-tag>
            </template>
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.accessUrl')">
            <template v-if="appDetail.deployKey">
              <a :href="accessUrl" target="_blank" rel="noopener">{{ accessUrl }}</a>
            </template>
            <template v-else>-</template>
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.deployedTime')">
            {{ formatDateTime(appDetail.deployedTime) || '-' }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.createTime')">
            {{ formatDateTime(appDetail.createTime) || '-' }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('appEdit.updateTime')">
            {{ formatDateTime(appDetail.updateTime) || '-' }}
          </a-descriptions-item>
        </a-descriptions>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
#appEditPage {
  max-width: 720px;
  margin: 0 auto;
}

.title {
  margin: 0 0 28px;
  font-size: 22px;
  font-weight: 600;
}

.app-edit-form {
  padding-bottom: 28px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 28px;
}

.app-detail {
  max-width: 720px;
  margin-bottom: 32px;
}

.detail-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
}

.mono-cell {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
}
</style>
