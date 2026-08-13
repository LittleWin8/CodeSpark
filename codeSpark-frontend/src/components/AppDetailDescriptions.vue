<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/utils/time'
import { getDeployUrl } from '@/utils/url'
import { useCodeGenType } from '@/constants/codeGenType'

const props = defineProps<{
  app: API.AppVO
}>()

const { t } = useI18n()
const { label: codeGenTypeLabel, color: codeGenTypeColor } = useCodeGenType()

const accessUrl = computed(() => getDeployUrl(props.app.deployKey))
</script>

<template>
  <a-descriptions :column="2" size="default" bordered>
    <a-descriptions-item :label="t('appEdit.appId')">
      <span class="mono-cell">{{ app.id }}</span>
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.codeGenType')">
      <a-tag :color="codeGenTypeColor(app.codeGenType)">{{ codeGenTypeLabel(app.codeGenType) }}</a-tag>
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.creator')">
      {{ app.user?.userName || app.userId }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.deployKey')">
      <template v-if="app.deployKey">
        <a-tag color="green">{{ t('appEdit.deployed') }}</a-tag>
        <span class="mono-cell">{{ app.deployKey }}</span>
      </template>
      <template v-else>
        <a-tag>{{ t('appEdit.notDeployed') }}</a-tag>
      </template>
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.accessUrl')">
      <template v-if="app.deployKey && accessUrl">
        <a :href="accessUrl" target="_blank" rel="noopener">{{ accessUrl }}</a>
      </template>
      <template v-else>-</template>
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.deployedTime')">
      {{ formatDateTime(app.deployedTime) || '-' }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.createTime')">
      {{ formatDateTime(app.createTime) || '-' }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('appEdit.updateTime')">
      {{ formatDateTime(app.updateTime) || '-' }}
    </a-descriptions-item>
  </a-descriptions>
</template>

<style scoped>
.mono-cell {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
}
</style>
