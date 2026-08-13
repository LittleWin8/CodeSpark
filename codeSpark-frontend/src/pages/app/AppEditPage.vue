<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Upload } from 'ant-design-vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import { deleteApp, getAppVoById, updateApp } from '@/api/appController'
import AppDetailDescriptions from '@/components/AppDetailDescriptions.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { uploadCover } from '@/utils/upload'

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
  cover: '',
})

const sameId = (a?: string | number, b?: string | number) => String(a ?? '') === String(b ?? '')

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
      formState.cover = data.cover ?? ''
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

/**
 * 本地上传封面：调 /file/upload/cover，成功后回填 URL
 */
const handleCoverUpload = async (options: UploadRequestOption) => {
  if (!appId.value) {
    message.error(t('appEdit.loadFailed'))
    return
  }
  try {
    const res = await uploadCover(appId.value, options.file as File)
    if (res.data.code === 0 && res.data.data) {
      formState.cover = res.data.data
      message.success(t('appEdit.coverUploadSuccess'))
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('appEdit.saveFailed'))
    }
  } catch {
    message.error(t('appEdit.saveFailed'))
  }
}

const handleSubmit = async () => {
  saving.value = true
  try {
    const idParam = appId.value as unknown as number
    const res = await updateApp({
      id: idParam,
      appName: formState.appName,
      cover: formState.cover,
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

          <a-form-item :label="t('appEdit.cover')" name="cover">
            <div class="cover-row">
              <a-input
                v-model:value="formState.cover"
                :placeholder="t('appEdit.coverPlaceholder')"
                class="cover-input"
              />
              <Upload
                :show-upload-list="false"
                :custom-request="handleCoverUpload"
                accept="image/*"
              >
                <a-button class="cover-upload-btn">
                  <template #icon><CloudUploadOutlined /></template>
                  {{ t('appEdit.coverUpload') }}
                </a-button>
              </Upload>
            </div>
            <img
              v-if="formState.cover"
              :src="formState.cover"
              alt="cover"
              class="cover-preview"
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
        <AppDetailDescriptions :app="appDetail" />
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

.cover-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-input {
  flex: 1;
  min-width: 0;
}

.cover-upload-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.cover-preview {
  margin-top: 8px;
  max-width: 160px;
  max-height: 90px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #f0f0f0;
}
</style>
