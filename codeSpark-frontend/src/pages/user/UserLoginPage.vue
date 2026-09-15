<template>
  <div id="userLoginPage">
    <h2 class="title">{{ t('login.title') }}</h2>
    <div class="desc">{{ t('login.desc') }}</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: t('login.accountRequired') }]">
        <a-input v-model:value="formState.userAccount" :placeholder="t('login.accountPlaceholder')" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: t('login.passwordRequired') },
          { min: 8, message: t('login.passwordMinLength') },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" :placeholder="t('login.passwordPlaceholder')" />
      </a-form-item>
      <div class="tips">
        <a href="javascript:void(0)" @click="openReset">{{ t('login.forgotPassword') }}</a>
        <span class="tips-gap" />
        {{ t('login.noAccount') }}
        <RouterLink to="/user/register">{{ t('login.goRegister') }}</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">{{ t('common.login') }}</a-button>
      </a-form-item>
    </a-form>

    <a-modal
      v-model:open="resetVisible"
      :title="t('resetPassword.title')"
      :confirm-loading="resetting"
      :ok-text="t('resetPassword.submit')"
      @ok="handleResetSubmit"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('register.emailPlaceholder')">
          <a-input v-model:value="resetForm.userEmail" :placeholder="t('resetPassword.emailPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('resetPassword.codePlaceholder')">
          <div style="display: flex; gap: 8px">
            <a-input v-model:value="resetForm.emailCode" :placeholder="t('resetPassword.codePlaceholder')" />
            <a-button :disabled="resetCooldown > 0" :loading="resetSending" @click="handleSendResetCode">
              {{ resetCooldown > 0 ? `${resetCooldown}s` : t('resetPassword.sendCode') }}
            </a-button>
          </div>
        </a-form-item>
        <a-form-item :label="t('resetPassword.newPasswordPlaceholder')">
          <a-input-password v-model:value="resetForm.newPassword" :placeholder="t('resetPassword.newPasswordPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('resetPassword.checkPasswordPlaceholder')">
          <a-input-password v-model:value="resetForm.checkPassword" :placeholder="t('resetPassword.checkPasswordPlaceholder')" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { resetPasswordByEmail, sendEmailCode } from '@/api/emailController'
import { userLogin } from '@/api/userController'
import { message } from 'ant-design-vue'
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'

const { t } = useI18n()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

// ---- 忘记密码：邮箱验证码重置 ----
const resetVisible = ref(false)
const resetting = ref(false)
const resetSending = ref(false)
const resetCooldown = ref(0)
let resetTimer: ReturnType<typeof setInterval> | null = null
const resetForm = reactive<API.ResetPasswordByEmailRequest>({
  userEmail: '',
  emailCode: '',
  newPassword: '',
  checkPassword: '',
})

const stopResetCooldown = () => {
  if (resetTimer) {
    clearInterval(resetTimer)
    resetTimer = null
  }
}

const openReset = () => {
  resetVisible.value = true
  resetCooldown.value = 0
}

const handleSendResetCode = async () => {
  if (!resetForm.userEmail) {
    message.warning(t('resetPassword.emailPlaceholder'))
    return
  }
  resetSending.value = true
  try {
    const res = await sendEmailCode({ userEmail: resetForm.userEmail, scene: 'reset_password' })
    if (res.data.code === 0) {
      message.success(t('resetPassword.codeSent'))
      resetCooldown.value = 60
      stopResetCooldown()
      resetTimer = setInterval(() => {
        resetCooldown.value--
        if (resetCooldown.value <= 0) {
          stopResetCooldown()
        }
      }, 1000)
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message))
    }
  } catch {
    message.error(t('resetPassword.sendFailed'))
  } finally {
    resetSending.value = false
  }
}

const handleResetSubmit = async () => {
  const { userEmail, emailCode, newPassword, checkPassword } = resetForm
  if (!userEmail || !emailCode || !newPassword || !checkPassword) {
    message.warning(t('error.emptyParams'))
    return
  }
  if (newPassword !== checkPassword) {
    message.warning(t('register.passwordMismatch'))
    return
  }
  resetting.value = true
  try {
    const res = await resetPasswordByEmail(resetForm)
    if (res.data.code === 0) {
      message.success(t('resetPassword.success'))
      resetVisible.value = false
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message))
    }
  } catch {
    message.error(getErrorMessage())
  } finally {
    resetting.value = false
  }
}

onBeforeUnmount(stopResetCooldown)

/**
 * 提交表单
 */
const handleSubmit = async (values: API.UserLoginRequest) => {
  const res = await userLogin(values)
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success(t('login.loginSuccess'))
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error(t('login.loginFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}
</script>

<style scoped>
#userLoginPage {
  max-width: 400px;
  margin: 0 auto;
  padding: 8px 0;
}

.title {
  text-align: center;
  margin-bottom: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
}

.desc {
  text-align: center;
  color: #8a8a8e;
  margin-bottom: 28px;
  font-size: 14px;
}

.tips {
  margin-bottom: 16px;
  color: #8a8a8e;
  font-size: 13px;
  text-align: right;
}

.tips-gap {
  display: inline-block;
  width: 12px;
}

.tips a {
  color: #1f8f7a;
}
</style>
