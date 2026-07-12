<template>
  <a-dropdown>
    <a-button class="lang-btn">
      <GlobalOutlined />
      {{ currentLocale === 'zh-CN' ? '中文' : 'English' }}
    </a-button>
    <template #overlay>
      <a-menu @click="handleLocaleChange">
        <a-menu-item key="zh-CN">
          <span>中文</span>
        </a-menu-item>
        <a-menu-item key="en-US">
          <span>English</span>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { GlobalOutlined } from '@ant-design/icons-vue'

const { locale } = useI18n()

const currentLocale = computed(() => locale.value)

const handleLocaleChange = ({ key }: { key: string }) => {
  locale.value = key
  localStorage.setItem('locale', key)
}
</script>

<style scoped>
.lang-btn {
  min-width: 100px;
}
</style>
