<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="site-line">
        <a
          :href="siteUrl || '/'"
          class="site-link"
        >
          <span class="copyright-mark">© </span>
          <span class="site-name">{{ siteName }}</span>
          <span class="slogan">· {{ t('footer.slogan') }}</span>
        </a>
      </p>
      <p class="meta-line">
        <template v-for="(item, index) in metaItems" :key="item.key">
          <span v-if="index > 0" class="divider">|</span>
          <a
            v-if="item.href"
            :href="item.href"
            target="_blank"
            rel="noopener noreferrer"
            class="meta-link"
          >
            <img
              v-if="item.icon"
              :src="item.icon"
              alt=""
              class="meta-icon"
              @error="onIconError($event)"
            >
            {{ item.label }}
          </a>
          <router-link
            v-else
            :to="item.to ?? ''"
            class="meta-link"
          >
            {{ item.label }}
          </router-link>
        </template>
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  gaBeianIcon,
  gaBeianNumber,
  gaBeianUrl,
  icpNumber,
  icpUrl,
  siteName,
  siteUrl,
  webmasterName,
  webmasterUrl,
} from '@/config/site'

const { t } = useI18n()

interface FooterMetaItem {
  key: string
  label: string
  href?: string
  to?: string
  icon?: string
}

const metaItems = computed<FooterMetaItem[]>(() => {
  const items: FooterMetaItem[] = []
  if (webmasterName) {
    items.push({ key: 'webmaster', label: `站长 ${webmasterName}`, href: webmasterUrl || undefined })
  }
  items.push(
    { key: 'privacy', label: '隐私政策', to: '/privacy' },
    { key: 'terms', label: '用户协议', to: '/terms' },
  )
  if (icpNumber) {
    items.push({ key: 'icp', label: icpNumber, href: icpUrl })
  }
  if (gaBeianNumber) {
    items.push({ key: 'ga', label: gaBeianNumber, href: gaBeianUrl, icon: gaBeianIcon || undefined })
  }
  return items
})

/** 图标加载失败（如自托管资源被清理）时隐藏，避免出现裂图 */
function onIconError(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}
</script>

<style scoped>
.footer {
  background: #efefef;
  text-align: center;
  padding: 16px 20px 18px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
}

.site-line {
  margin: 0 0 4px;
  font-size: 14px;
}
.site-link {
  text-decoration: none;
}
.copyright-mark {
  color: #999;
  font-size: 12px;
  margin-right: 2px;
}
.site-name {
  color: #1f8f7a;
  font-weight: 600;
}
.slogan {
  color: #999;
  margin-left: 4px;
}
.site-link:hover .site-name {
  color: #157a68;
}

.meta-line {
  margin: 0;
  color: #999;
  font-size: 12px;
}
.meta-link {
  color: #999;
  text-decoration: none;
  transition: color 0.2s;
}
.meta-link:hover {
  color: #1f8f7a;
}
.meta-icon {
  width: 14px;
  height: 15px;
  vertical-align: -3px;
  margin-right: 3px;
}
.divider {
  margin: 0 8px;
  color: #d9d9d9;
}
</style>
