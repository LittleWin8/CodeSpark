<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()

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
  <a-config-provider :theme="themeConfig">
    <BasicLayout />
  </a-config-provider>
</template>

<style>
</style>
