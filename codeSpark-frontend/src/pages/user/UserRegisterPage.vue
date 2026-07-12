<template>
  <div id="userRegisterPage">
    <h2 class="title">{{ t('register.title') }}</h2>
    <div class="desc">{{ t('register.desc') }}</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: t('register.accountRequired') }]">
        <a-input v-model:value="formState.userAccount" :placeholder="t('register.accountPlaceholder')" />
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

const { t } = useI18n()
const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

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
    message.error(t('register.registerFailed') + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}
</style>
