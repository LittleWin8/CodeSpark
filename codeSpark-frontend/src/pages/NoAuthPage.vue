<template>
  <div id="noAuthPage">
    <a-result status="403" :title="t('noAuth.title')" :sub-title="t('noAuth.subtitle')">
      <template #extra>
        <a-button type="primary" @click="goHome">{{ t('noAuth.backHome') }}</a-button>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>
