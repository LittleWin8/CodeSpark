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
        {{ t('login.noAccount') }}
        <RouterLink to="/user/register">{{ t('login.goRegister') }}</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">{{ t('common.login') }}</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { userLogin } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const { t } = useI18n()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

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
    message.error(t('login.loginFailed') + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
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
