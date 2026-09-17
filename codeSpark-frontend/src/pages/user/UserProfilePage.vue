<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Upload } from 'ant-design-vue'
import {
  CameraOutlined,
  CopyOutlined,
  EditOutlined,
  InfoCircleOutlined,
  SaveOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { useQuotaStore } from '@/stores/quota'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import { useRouter } from 'vue-router'
import { updateMyUser, updateUserPassword } from '@/api/userController'
import { getTotalTokens } from '@/api/userTokenUsageController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime } from '@/utils/time'
import { formatTokens } from '@/utils/formatTokens'
import { uploadAvatar } from '@/utils/upload'
import { resolveFileUrl } from '@/utils/storage'

const { t } = useI18n()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const quotaStore = useQuotaStore()

// 额度卡颜色：与顶部额度环一致（绿/橙/红）
const quotaColor = computed(() => {
  switch (quotaStore.level) {
    case 'danger':
      return '#ff4d4f'
    case 'warning':
      return '#faad14'
    default:
      return '#1f8f7a'
  }
})

const routerPushLogin = async () => {
  await router.push(`/user/login?redirect=${encodeURIComponent('/user/profile')}`)
}

const saving = ref(false)
const avatarUploading = ref(false)

// 累计 Token 消耗（个人中心展示用）
const totalTokens = ref<number>()

const formState = reactive({
  userName: '',
  // 头像展示值（上传后为解析后的 URL，供预览）
  userAvatar: '',
  // 头像存储标识（oss: / local:）：提交入库用，与展示 URL 分离
  userAvatarKey: '',
  userProfile: '',
})

onMounted(() => {
  formState.userName = loginUserStore.loginUser.userName || ''
  // 回显：userAvatar 为解析后的 URL（展示），userAvatarKey 为原始存储标识（提交），兼容无 key 的存量数据
  formState.userAvatar = loginUserStore.loginUser.userAvatar || ''
  formState.userAvatarKey = loginUserStore.loginUser.userAvatarKey || ''
  formState.userProfile = loginUserStore.loginUser.userProfile || ''
  // 额度数据（个人中心进入页面即刷新）
  quotaStore.fetchQuota()
  // 累计 Token 消耗（失败静默，展示 '-'）
  getTotalTokens()
    .then((res) => {
      if (res.data.code === 0 && res.data.data !== null && res.data.data !== undefined) {
        totalTokens.value = Number(res.data.data)
      }
    })
    .catch(() => {})
})

// 角色展示
const roleLabel = computed(() =>
  loginUserStore.loginUser.userRole === 'admin' ? t('userManage.roleAdmin') : t('userManage.roleUser'),
)
const roleTagColor = computed(() => (loginUserStore.loginUser.userRole === 'admin' ? 'green' : 'blue'))

/**
 * 上传头像：上传即生效（后端直接更新头像字段），成功后刷新全局用户信息并提示
 */
const handleAvatarUpload = async (options: UploadRequestOption) => {
  avatarUploading.value = true
  try {
    const res = await uploadAvatar(options.file as File)
    if (res.data.code === 0 && res.data.data) {
      // 上传返回存储标识：提交用标识、预览用解析后的 URL（避免 oss:/local: 标识直接当图片 src 裂图）
      const storageKey = res.data.data
      const url = await resolveFileUrl(storageKey)
      formState.userAvatarKey = storageKey
      formState.userAvatar = url || storageKey
      // 后端已更新头像，刷新全局登录用户信息（顶栏等即时生效）
      await loginUserStore.fetchLoginUser()
      message.success(t('profile.avatarUpdateSuccess'))
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

// 修改密码表单
const editTab = ref<'profile' | 'password'>('profile')
const pwdForm = reactive({ oldPassword: '', newPassword: '', checkPassword: '' })
const pwdSaving = ref(false)

/**
 * 修改密码：成功后所有会话失效，需用新密码重新登录
 */
const handlePasswordSubmit = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.checkPassword) {
    message.warning(t('profile.pwdAllRequired'))
    return
  }
  if (pwdForm.newPassword !== pwdForm.checkPassword) {
    message.warning(t('userManage.pwdMismatch'))
    return
  }
  pwdSaving.value = true
  try {
    const res = await updateUserPassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
      checkPassword: pwdForm.checkPassword,
    })
    if (res.data.code === 0) {
      message.success(t('profile.pwdChangeSuccess'))
      // 所有会话已失效，回到登录页重新登录
      loginUserStore.setLoginUser({ userName: t('header.anonymous') })
      await routerPushLogin()
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('profile.saveFailed'))
    }
  } finally {
    pwdSaving.value = false
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
      // 头像由 uploadAvatar 上传接口单独管理（上传即生效），此处不提交
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

        <!-- 卡片2：额度用量（替代 VIP 卡，避免虚假会员宣传） -->
        <a-card class="profile-card" :bordered="false">
          <template #title>
            <span class="card-title"><ThunderboltOutlined /> {{ t('profile.quotaCardTitle') }}</span>
          </template>
          <template v-if="quotaStore.info && !quotaStore.info.unlimited">
            <a-progress
              :percent="quotaStore.remainingPercent"
              :show-info="false"
              :stroke-color="quotaColor"
              trail-color="#e5e6eb"
              size="small"
            />
            <div class="vip-item">
              <span class="item-label">{{ t('profile.quotaUsed') }}</span>
              <span class="item-value mono-cell">
                {{ formatTokens(quotaStore.info.usedTokens) }} /
                {{ formatTokens(quotaStore.info.monthlyLimit) }}
              </span>
            </div>
            <div class="vip-item">
              <span class="item-label">{{ t('profile.quotaRemaining') }}</span>
              <span class="item-value mono-cell">
                {{ formatTokens(quotaStore.info.remainingTokens) }}
              </span>
            </div>
          </template>
          <div v-else class="vip-item">
            <span class="item-label">{{ t('profile.quotaUsed') }}</span>
            <span class="item-value mono-cell">{{ t('profile.unlimited') }}</span>
          </div>
          <div class="vip-item">
            <span class="item-label">{{ t('profile.totalTokens') }}</span>
            <span class="item-value mono-cell">{{ formatTokens(totalTokens) }}</span>
          </div>
          <div class="quota-explain">{{ t('profile.quotaExplain') }}</div>
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

        <!-- 卡片4：编辑资料 / 修改密码（Tab 切换） -->
        <a-card class="profile-card edit-card" :bordered="false">
          <template #title>
            <span class="card-title"><EditOutlined /> {{ t('profile.editTitle') }}</span>
          </template>
          <a-tabs v-model:activeKey="editTab">
            <a-tab-pane key="profile" :tab="t('profile.tabProfile')">
              <a-form :model="formState" layout="vertical" @finish="handleSubmit">
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
            </a-tab-pane>
            <a-tab-pane key="password" :tab="t('profile.tabPassword')">
              <a-form :model="pwdForm" layout="vertical" @finish="handlePasswordSubmit">
                <a-form-item :label="t('profile.oldPassword')" name="oldPassword">
                  <a-input-password v-model:value="pwdForm.oldPassword" />
                </a-form-item>
                <a-form-item :label="t('profile.newPassword')" name="newPassword">
                  <a-input-password v-model:value="pwdForm.newPassword" :maxlength="64" />
                </a-form-item>
                <a-form-item :label="t('profile.confirmNewPassword')" name="checkPassword">
                  <a-input-password v-model:value="pwdForm.checkPassword" :maxlength="64" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" html-type="submit" :loading="pwdSaving">
                    {{ t('profile.pwdSubmit') }}
                  </a-button>
                </a-form-item>
              </a-form>
            </a-tab-pane>
          </a-tabs>
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

.quota-explain {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  line-height: 1.6;
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

.edit-card :deep(.ant-card-body) {
  padding-top: 8px;
}

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
