<script setup>
const year = new Date().getFullYear()
</script>

<template>
  <footer class="footer">
    <div class="container footer-inner">
      <div class="footer-brand">
        <p class="footer-title">{{appName}}</p>
        <p class="footer-desc">
          一个安静写字的地方。关于阅读、写作，和一些慢慢想明白的事。
        </p>
      </div>
      <div class="footer-meta">
        <router-link to="/" class="footer-link">文章归档</router-link>
        <router-link to="/about" class="footer-link">关于作者</router-link>
      </div>
    </div>
    <div class="container footer-bottom">
      <span>© {{ year }} 林知远 · 保留所有权利</span>
      <span>用文字抵抗遗忘</span>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: 96px;
  border-top: 1px solid var(--line);
  padding-top: 48px;
}

.footer-inner {
  display: flex;
  justify-content: space-between;
  gap: 32px;
  flex-wrap: wrap;
}

.footer-title {
  font-size: 1rem;
  letter-spacing: 0.16em;
  margin: 0 0 8px;
}

.footer-desc {
  margin: 0;
  max-width: 360px;
  color: var(--ink-soft);
  font-size: 0.9rem;
}

.footer-meta {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}

.footer-link {
  font-size: 0.9rem;
  color: var(--ink-soft);
  transition: color 0.2s ease;
}

.footer-link:hover {
  color: var(--accent);
}

.footer-bottom {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 40px;
  padding: 20px 24px 8px;
  border-top: 1px solid var(--line);
  color: var(--ink-mute);
  font-size: 0.78rem;
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.06em;
}

@media (max-width: 640px) {
  .footer {
    margin-top: 64px;
  }
}
</style>
