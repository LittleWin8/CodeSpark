<template>
  <a-layout class="basic-layout">
    <GlobalHeader v-if="!hideHeader" />

    <a-layout-content :class="contentClass">
      <router-view />
    </a-layout-content>

    <GlobalFooter v-if="!hideFooter" />
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()

const hideHeader = computed(() => Boolean(route.meta.hideHeader))
const hideFooter = computed(() => Boolean(route.meta.hideFooter))
const fullWidth = computed(() => Boolean(route.meta.fullWidth))

const contentClass = computed(() => ({
  'main-content': true,
  'main-content--full': fullWidth.value,
  'main-content--wide': Boolean(route.meta.wideContent),
  'main-content--no-footer': hideFooter.value,
}))
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #e8f6f4 0%, #f4fafa 24%, #f5f5f7 100%);
}

.main-content {
  max-width: 1200px;
  width: 100%;
  padding: 24px;
  background: #fff;
  margin: 16px auto 72px;
  border-radius: 16px;
  box-sizing: border-box;
}

.main-content--wide {
  max-width: min(1680px, calc(100vw - 48px));
}

.main-content--full {
  max-width: none;
  margin: 0;
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.main-content--no-footer {
  margin-bottom: 0;
}
</style>
