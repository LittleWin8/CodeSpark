<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Upload } from 'ant-design-vue'
import {
  CameraOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  CrownOutlined,
  EditOutlined,
  InfoCircleOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import { updateMyUser } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime } from '@/utils/time'
import { uploadAvatar } from '@/utils/upload'

const { t } = useI18n()
const loginUserStore = useLoginUserStore()

const saving = ref(false)
const avatarUploading = ref(false)

const formState = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

onMounted(() => {
  formState.userName = loginUserStore.loginUser.userName || ''
  formState.userAvatar = loginUserStore.loginUser.userAvatar || ''
  formState.userProfile = loginUserStore.loginUser.userProfile || ''
})

// 角色展示
const roleLabel = computed(() =>
  loginUserStore.loginUser.userRole === 'admin' ? t('userManage.roleAdmin') : t('userManage.roleUser'),
)
const roleTagColor = computed(() => (loginUserStore.loginUser.userRole === 'admin' ? 'green' : 'blue'))

/**
 * 上传头像：调后端 /file/upload/avatar，成功后回填 URL
 */
const handleAvatarUpload = async (options: UploadRequestOption) => {
  avatarUploading.value = true
  try {
    const res = await uploadAvatar(options.file as File)
    if (res.data.code === 0 && res.data.data) {
      formState.userAvatar = res.data.data
      message.success(t('profile.avatarUploadSuccess'))
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('profile.saveFailed'))
    }
  } catch {
    message.error(t('profile.saveFailed'))
  } finally {
    avatarUploading.value = false
  }
}

/**
 * 复制分享码
 */
const copyShareCode = async () => {
  const code = loginUserStore.loginUser.shareCode
  if (!code) {
    return
  }
  try {
    await navigator.clipboard.writeText(code)
    message.success(t('profile.copySuccess'))
  } catch {
    message.error(t('profile.copyFailed'))
  }
}

/**
 * 保存个人信息
 */
const handleSubmit = async () => {
  if (!formState.userName.trim()) {
    message.warning(t('profile.userNameRequired'))
    return
  }
  saving.value = true
  try {
    const res = await updateMyUser({
      userName: formState.userName.trim(),
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile.trim(),
    })
    if (res.data.code === 0) {
      message.success(t('profile.saveSuccess'))
      // 刷新登录用户信息
      await loginUserStore.fetchLoginUser()
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('profile.saveFailed'))
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div id="userProfilePage">
    <h2 class="title">{{ t('profile.title') }}</h2>

    <div class="profile-layout">
      <!-- 左侧：身份卡 + VIP 卡 -->
      <div class="profile-left">
        <!-- 卡片1：头像、昵称、账户、角色 -->
        <a-card class="profile-card" :bordered="false">
          <div class="identity-body">
            <Upload
              :show-upload-list="false"
              :custom-request="handleAvatarUpload"
              accept="image/*"
            >
              <div class="avatar-wrap" :class="{ 'is-uploading': avatarUploading }">
                <a-avatar :size="88" :src="formState.userAvatar">
                  {{ formState.userName?.slice(0, 1) || 'U' }}
                </a-avatar>
                <div class="avatar-overlay">
                  <CameraOutlined />
                  <span>{{ t('profile.changeAvatar') }}</span>
                </div>
              </div>
            </Upload>
            <div class="profile-nickname">{{ formState.userName || t('header.anonymous') }}</div>
            <div class="profile-account">{{ loginUserStore.loginUser.userAccount }}</div>
            <a-tag :color="roleTagColor">{{ roleLabel }}</a-tag>
          </div>
        </a-card>

        <!-- 卡片2：VIP 会员服务 -->
        <a-card class="profile-card" :bordered="false">
          <template #title>
            <span class="card-title"><CrownOutlined /> {{ t('profile.vipService') }}</span>
          </template>
          <div class="vip-item">
            <span class="item-label">{{ t('profile.memberStatus') }}</span>
            <template v-if="loginUserStore.loginUser.vipExpireTime">
              <a-tag color="gold">{{ t('profile.member') }}</a-tag>
            </template>
            <span v-else class="item-value">{{ t('profile.notMember') }}</span>
          </div>
          <div class="vip-item">
            <span class="item-label">{{ t('profile.validity') }}</span>
            <span class="item-value">
              {{ formatDateTime(loginUserStore.loginUser.vipExpireTime) || '-' }}
            </span>
          </div>
          <div class="vip-item">
            <span class="item-label">{{ t('profile.memberNumber') }}</span>
            <span class="item-value">{{ loginUserStore.loginUser.vipNumber || '-' }}</span>
          </div>
        </a-card>
      </div>

      <!-- 右侧：账户信息 + 编辑资料 -->
      <div class="profile-right">
        <!-- 卡片3：账户信息 -->
        <a-card class="profile-card" :bordered="false">
          <template #title>
            <span class="card-title"><InfoCircleOutlined /> {{ t('profile.accountInfo') }}</span>
          </template>
          <div class="info-item">
            <span class="item-label">{{ t('profile.email') }}</span>
            <span class="item-value email-value">{{ loginUserStore.loginUser.userEmail || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="item-label">{{ t('profile.registerTime') }}</span>
            <span class="item-value">
              {{ formatDateTime(loginUserStore.loginUser.createTime) || '-' }}
            </span>
          </div>
          <div class="info-item">
            <span class="item-label">{{ t('profile.shareCode') }}</span>
            <span v-if="loginUserStore.loginUser.shareCode" class="item-value">
              <span class="mono-cell share-code">{{ loginUserStore.loginUser.shareCode }}</span>
              <a-button size="small" @click="copyShareCode">
                <template #icon><CopyOutlined /></template>
                {{ t('profile.copy') }}
              </a-button>
            </span>
            <span v-else class="item-value">-</span>
          </div>
        </a-card>

        <!-- 卡片4：编辑资料 -->
        <a-card class="profile-card edit-card" :bordered="false">
          <template #title>
            <span class="card-title"><EditOutlined /> {{ t('profile.editTitle') }}</span>
          </template>
          <a-form :model="formState" layout="vertical" @finish="handleSubmit">
            <a-form-item :label="t('profile.avatarUrl')" name="userAvatar">
              <div class="avatar-url-row">
                <a-input
                  v-model:value="formState.userAvatar"
                  :placeholder="t('profile.avatarUrlPlaceholder')"
                  class="avatar-url-input"
                />
                <Upload
                  :show-upload-list="false"
                  :custom-request="handleAvatarUpload"
                  accept="image/*"
                >
                  <a-button class="avatar-upload-btn">
                    <template #icon><CloudUploadOutlined /></template>
                    {{ t('profile.uploadAvatar') }}
                  </a-button>
                </Upload>
              </div>
            </a-form-item>
            <a-form-item :label="t('profile.userName')" name="userName">
              <a-input v-model:value="formState.userName" :maxlength="20" show-count />
            </a-form-item>
            <a-form-item :label="t('profile.userProfile')" name="userProfile">
              <a-textarea
                v-model:value="formState.userProfile"
                :rows="4"
                :maxlength="100"
                show-count
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="saving">
                <template #icon><SaveOutlined /></template>
                {{ t('common.save') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
#userProfilePage {
  max-width: 960px;
  margin: 0 auto;
}

.title {
  margin: 0 0 24px;
  font-size: 22px;
  font-weight: 600;
}

.profile-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.profile-left {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-card {
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

/* 卡片1：身份 */
.identity-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
}

.avatar-wrap.is-uploading {
  opacity: 0.6;
  pointer-events: none;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-overlay {
  opacity: 1;
}

.profile-nickname {
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
}

.profile-account {
  font-size: 14px;
  color: #86868b;
}

/* 信息行（VIP / 账户信息共用） */
.vip-item,
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.vip-item:last-child,
.info-item:last-child {
  border-bottom: none;
}

.item-label {
  color: #86868b;
  font-size: 14px;
  white-space: nowrap;
  margin-right: 12px;
}

.item-value {
  font-size: 14px;
  color: #1d1d1f;
  display: inline-flex;
  align-items: center;
}

.mono-cell {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
}

.share-code {
  margin-right: 12px;
}

.email-value {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  justify-content: flex-end;
}

/* 编辑资料卡紧凑化：缩小表单项间距，压低右侧高度 */
.edit-card :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.edit-card :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

/* 头像地址：输入框 + 本地上传按钮同行 */
.avatar-url-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-url-input {
  flex: 1;
  min-width: 0;
}

.avatar-upload-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .profile-layout {
    flex-direction: column;
  }

  .profile-left {
    width: 100%;
  }
}
</style>
