<template>
  <div id="userRegisterPage">
    <h2 class="title">{{ t('register.title') }}</h2>
    <div class="desc">{{ t('register.desc') }}</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: t('register.accountRequired') }]">
        <a-input v-model:value="formState.userAccount" :placeholder="t('register.accountPlaceholder')" />
      </a-form-item>
      <a-form-item
        name="userEmail"
        :rules="[
          { required: true, message: t('register.emailRequired') },
          { validator: validateEmail },
        ]"
      >
        <a-input v-model:value="formState.userEmail" :placeholder="t('register.emailPlaceholder')" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: t('register.passwordRequired') },
          { min: 8, message: t('register.passwordMinLength') },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" :placeholder="t('register.passwordPlaceholder')" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: t('register.checkPasswordRequired') },
          { min: 8, message: t('register.passwordMinLength') },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" :placeholder="t('register.checkPasswordPlaceholder')" />
      </a-form-item>
      <div class="tips">
        {{ t('register.hasAccount') }}
        <RouterLink to="/user/login">{{ t('register.goLogin') }}</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">{{ t('common.register') }}</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { reactive } from 'vue'
import { getErrorMessage } from '@/utils/errorMessage'

const { t } = useI18n()
const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userEmail: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证邮箱格式（空值交给 required 规则处理，避免重复提示）
 */
const validateEmail = (_rule: Rule, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback()
    return
  }
  const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  if (!emailRegex.test(value)) {
    callback(new Error(t('register.emailInvalid')))
    return
  }
  callback()
}

/**
 * 验证确认密码
 */
const validateCheckPassword = (_rule: Rule, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error(t('register.passwordMismatch')))
  } else {
    callback()
  }
}

/**
 * 提交表单
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  if (res.data.code === 0) {
    message.success(t('register.registerSuccess'))
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error(t('register.registerFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}
</script>

<style scoped>
#userRegisterPage {
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

.tips a {
  color: #1f8f7a;
}
</style>
