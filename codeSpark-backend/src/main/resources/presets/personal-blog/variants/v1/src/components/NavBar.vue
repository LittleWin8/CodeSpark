<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const open = ref(false)

const links = [
  { name: 'home', label: '首页', to: '/' },
  { name: 'archive', label: '归档', to: '/archive' },
  { name: 'about', label: '关于', to: '/about' }
]

watch(
  () => route.fullPath,
  () => {
    open.value = false
  }
)
</script>

<template>
  <header class="nav">
    <div class="container nav-inner">
      <router-link to="/" class="brand">
        <span class="brand-mark">静</span>
        <span class="brand-text">
          <strong>{{appName}}</strong>
          <em>阅读 · 写作 · 生活</em>
        </span>
      </router-link>

      <nav class="nav-links" :class="{ open }">
        <router-link
          v-for="link in links"
          :key="link.name"
          :to="link.to"
          class="nav-link"
          active-class="active"
        >
          {{ link.label }}
        </router-link>
      </nav>

      <button
        class="nav-toggle"
        :aria-expanded="open"
        aria-label="切换导航"
        @click="open = !open"
      >
        <span></span>
        <span></span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.nav {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(251, 250, 247, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
}

.nav-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 76px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--accent);
  color: var(--accent);
  border-radius: 50%;
  font-size: 1rem;
  letter-spacing: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 1.05rem;
  letter-spacing: 0.14em;
}

.brand-text em {
  font-style: normal;
  font-size: 0.68rem;
  letter-spacing: 0.18em;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 34px;
}

.nav-link {
  font-size: 0.95rem;
  color: var(--ink-soft);
  letter-spacing: 0.08em;
  padding: 6px 0;
  position: relative;
  transition: color 0.2s ease;
}

.nav-link::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: 0;
  width: 0;
  height: 1px;
  background: var(--accent);
  transition: width 0.25s ease;
}

.nav-link:hover {
  color: var(--ink);
}

.nav-link.active {
  color: var(--accent);
}

.nav-link.active::after {
  width: 100%;
}

.nav-toggle {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  width: 40px;
  height: 40px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
}

.nav-toggle span {
  display: block;
  width: 18px;
  height: 1px;
  margin: 0 auto;
  background: var(--ink-soft);
}

@media (max-width: 640px) {
  .nav-inner {
    height: 66px;
  }
  .nav-toggle {
    display: flex;
  }
  .nav-links {
    position: absolute;
    top: 66px;
    left: 0;
    right: 0;
    flex-direction: column;
    gap: 0;
    background: var(--bg);
    border-bottom: 1px solid var(--line);
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.28s ease;
  }
  .nav-links.open {
    max-height: 220px;
  }
  .nav-link {
    width: 100%;
    padding: 16px 24px;
    border-top: 1px solid var(--line);
  }
  .nav-link::after {
    display: none;
  }
}
</style>
