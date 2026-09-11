<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()

const { locale } = useI18n()

// antd 组件内置文案（Modal/Popconfirm 按钮、表格空数据、分页等）跟随 i18n 语言
const antdLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))

// 全局主题：统一品牌色（青绿），与顶部工具栏和谐一致
const themeConfig = {
  token: {
    colorPrimary: '#1f8f7a',
    colorInfo: '#1f8f7a',
    colorLink: '#1f8f7a',
    borderRadius: 8,
  },
}
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="antdLocale">
    <BasicLayout />
  </a-config-provider>
</template>

<style>
</style>
