<script setup>
import { articles } from '@/utils/articles'
</script>

<template>
  <div class="notfound container">
    <p class="code">404</p>
    <h1>这个页面好像不在了</h1>
    <p class="desc">也许链接输错了，也许那篇文章已经被合并到别处。下面这几篇也许你会喜欢。</p>
    <div class="picks">
      <router-link v-for="item in articles.slice(0, 3)" :key="item.id" :to="`/article/${item.id}`">
        <span>{{ item.date }}</span>
        <strong>{{ item.title }}</strong>
      </router-link>
    </div>
    <router-link to="/" class="btn">返回首页</router-link>
  </div>
</template>

<style scoped>
.notfound {
  padding: 110px 0 60px;
  text-align: center;
}

.code {
  font-size: 3.4rem;
  letter-spacing: 0.3em;
  color: var(--accent-soft);
  font-family: "Helvetica Neue", Arial, sans-serif;
  margin: 0;
}

h1 {
  font-size: 1.6rem;
  margin: 22px 0 16px;
}

.desc {
  font-size: 0.88rem;
  color: var(--ink-mute);
  max-width: 480px;
  margin: 0 auto 46px;
}

.picks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  max-width: 860px;
  margin: 0 auto 46px;
}

.picks a {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  padding: 24px 24px;
  transition: all 0.24s ease;
}

.picks a:hover {
  transform: translateY(-3px);
  border-color: #dcd6cb;
}

.picks span {
  font-size: 0.72rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.08em;
}

.picks strong {
  font-size: 0.94rem;
  font-weight: 500;
  line-height: 1.6;
}

.btn {
  display: inline-block;
  border: 1px solid var(--accent);
  border-radius: 999px;
  padding: 12px 34px;
  font-size: 0.84rem;
  letter-spacing: 0.1em;
  color: var(--accent);
  transition: all 0.22s ease;
}

.btn:hover {
  background: var(--accent);
  color: #fff;
}

@media (max-width: 760px) {
  .picks {
    grid-template-columns: 1fr;
  }
  .notfound {
    padding-top: 70px;
  }
}
</style>
