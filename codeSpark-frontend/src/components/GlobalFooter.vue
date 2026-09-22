<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="site-line">
        <a
          href="https://codespark.littlewin.top/"
          class="site-link"
        >
          <span class="copyright-mark">© </span>
          <span class="site-name">CodeSpark AI</span>
          <span class="slogan">· {{ t('footer.slogan') }}</span>
        </a>
      </p>
      <p class="meta-line">
        <a
          href="https://blog.littlewin.top/"
          target="_blank"
          rel="noopener noreferrer"
          class="meta-link"
        >
          站长 Little Win
        </a>
        <span class="divider">|</span>
        <router-link to="/privacy" class="meta-link">隐私政策</router-link>
        <span class="divider">|</span>
        <router-link to="/terms" class="meta-link">用户协议</router-link>
        <span class="divider">|</span>
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="meta-link"
        >
          辽ICP备2026022228号-1
        </a>
        <!-- 公安联网备案办下来后，在此处补充公安备案号并链接 beian.mps.gov.cn -->
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
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
.divider {
  margin: 0 8px;
  color: #d9d9d9;
}
</style>
