<script setup>
import { ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

function goLatest() {
  menuOpen.value = false
  if (route.path !== '/') {
    router.push('/')
    nextTick(() => scrollToLatest())
  } else {
    scrollToLatest()
  }
}

function scrollToLatest() {
  setTimeout(() => {
    const el = document.getElementById('latest')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 60)
}

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  }
)
</script>

<template>
  <div class="app">
    <header class="navbar">
      <div class="navbar-inner container">
        <router-link to="/" class="brand">
          <span class="brand-mark">静</span>
          <span class="brand-text">
            <strong>{{appName}}</strong>
            <em>前端 · 写作 · 日常</em>
          </span>
        </router-link>

        <nav class="nav-links">
          <router-link to="/" :class="{ current: route.path === '/' }">首页</router-link>
          <a href="javascript:void(0)" @click="goLatest">文章</a>
          <router-link to="/archive" :class="{ current: route.path === '/archive' }">
            归档
          </router-link>
          <router-link to="/about" :class="{ current: route.path === '/about' }">
            关于
          </router-link>
        </nav>

        <button class="menu-btn" type="button" @click="menuOpen = !menuOpen">
          {{ menuOpen ? '关闭' : '菜单' }}
        </button>
      </div>

      <div v-show="menuOpen" class="mobile-nav">
        <router-link to="/">首页</router-link>
        <a href="javascript:void(0)" @click="goLatest">文章</a>
        <router-link to="/archive">归档</router-link>
        <router-link to="/about">关于</router-link>
      </div>
    </header>

    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <footer class="footer">
      <div class="footer-inner container">
        <div class="footer-brand">
          <strong>{{appName}}</strong>
          <p>把想清楚的事写下来。这是一个不追热点的个人博客。</p>
        </div>
        <div class="footer-links">
          <div>
            <span>导航</span>
            <router-link to="/">首页</router-link>
            <router-link to="/archive">归档</router-link>
            <router-link to="/about">关于</router-link>
          </div>
          <div>
            <span>联系</span>
            <a href="mailto:hello@jing-shui.dev">邮箱</a>
            <a href="https://picsum.photos" target="_blank" rel="noreferrer">图源</a>
          </div>
        </div>
      </div>
      <div class="footer-bottom container">
        <span>© 2024 {{appName}} · 内容均为原创</span>
        <span>用心写字，慢慢更新</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(251, 250, 247, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
}

.navbar-inner {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.brand-text strong {
  font-size: 1.02rem;
  letter-spacing: 0.14em;
}

.brand-text em {
  font-style: normal;
  font-size: 0.68rem;
  color: var(--ink-mute);
  letter-spacing: 0.16em;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 34px;
  font-size: 0.9rem;
  color: var(--ink-soft);
}

.nav-links a {
  position: relative;
  padding: 4px 0;
  transition: color 0.22s ease;
}

.nav-links a::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 0;
  height: 1px;
  background: var(--accent);
  transition: width 0.26s ease;
}

.nav-links a:hover,
.nav-links a.current {
  color: var(--accent);
}

.nav-links a:hover::after,
.nav-links a.current::after {
  width: 100%;
}

.menu-btn {
  display: none;
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 999px;
  padding: 7px 18px;
  font-size: 0.82rem;
  color: var(--ink-soft);
}

.mobile-nav {
  display: none;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px 24px;
  border-top: 1px solid var(--line);
  font-size: 0.94rem;
  color: var(--ink-soft);
}

.main {
  flex: 1;
  padding-bottom: 96px;
}

.footer {
  border-top: 1px solid var(--line);
  background: #f6f4f0;
  padding-top: 56px;
}

.footer-inner {
  display: flex;
  justify-content: space-between;
  gap: 40px;
  flex-wrap: wrap;
  padding-bottom: 40px;
}

.footer-brand strong {
  font-size: 1.06rem;
  letter-spacing: 0.14em;
}

.footer-brand p {
  margin: 14px 0 0;
  font-size: 0.84rem;
  color: var(--ink-mute);
  max-width: 320px;
}

.footer-links {
  display: flex;
  gap: 70px;
  flex-wrap: wrap;
}

.footer-links div {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.footer-links span {
  font-size: 0.72rem;
  letter-spacing: 0.24em;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.footer-links a {
  font-size: 0.86rem;
  color: var(--ink-soft);
  transition: color 0.22s ease;
}

.footer-links a:hover {
  color: var(--accent);
}

.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 22px;
  padding-bottom: 28px;
  border-top: 1px solid var(--line);
  font-size: 0.74rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.06em;
}

@media (max-width: 860px) {
  .nav-links {
    display: none;
  }
  .menu-btn {
    display: block;
  }
  .mobile-nav {
    display: flex;
  }
}

@media (max-width: 640px) {
  .navbar-inner {
    height: 64px;
  }
  .main {
    padding-bottom: 64px;
  }
  .footer-links {
    gap: 40px;
  }
}
</style>
