<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ArticleCard from '@/components/ArticleCard.vue'
import { getArticleById, getRelatedArticles, author } from '@/utils/articles'

const route = useRoute()
const progress = ref(0)

const article = computed(() => getArticleById(route.params.id))
const related = computed(() => getRelatedArticles(article.value))

function onScroll() {
  const total = document.documentElement.scrollHeight - window.innerHeight
  const value = total > 0 ? (window.scrollY / total) * 100 : 0
  progress.value = Math.min(100, Math.max(0, value))
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="detail container">
    <div class="progress" :style="{ width: progress + '%' }"></div>

    <template v-if="article">
      <router-link to="/" class="back">← 返回首页</router-link>

      <header class="detail-head">
        <div class="detail-tags">
          <span v-for="tag in article.tags" :key="tag">{{ tag }}</span>
        </div>
        <h1>{{ article.title }}</h1>
        <div class="detail-meta">
          <img :src="author.avatar" :alt="author.name" />
          <div>
            <strong>{{ author.name }}</strong>
            <span>{{ article.date }} · 约 {{ article.readingTime }} 分钟阅读</span>
          </div>
        </div>
      </header>

      <figure class="detail-cover">
        <img :src="article.cover" :alt="article.title" />
      </figure>

      <article class="detail-body">
        <p class="lead">{{ article.summary }}</p>
        <template v-for="(block, index) in article.content" :key="index">
          <h2 v-if="block.h">{{ block.h }}</h2>
          <p v-else v-html="block.p"></p>
        </template>
      </article>

      <div class="detail-tail">
        <div class="tail-note">
          <strong>关于作者</strong>
          <p>{{ author.name }}，{{ author.role }}。相信慢一点想清楚，比快一点做完更重要。</p>
        </div>
        <router-link to="/" class="btn">继续浏览其他文章</router-link>
      </div>

      <section v-if="related.length" class="related">
        <p class="section-label">Read Next</p>
        <h2>相关阅读</h2>
        <div class="related-grid">
          <ArticleCard v-for="item in related" :key="item.id" :article="item" />
        </div>
      </section>
    </template>

    <div v-else class="not-found">
      <h1>找不到这篇文章</h1>
      <p>它可能已经被移动或重写，回到首页看看其他内容吧。</p>
      <router-link to="/" class="btn">返回首页</router-link>
    </div>
  </div>
</template>

<style scoped>
.detail {
  padding-top: 56px;
}

.progress {
  position: fixed;
  top: 74px;
  left: 0;
  height: 2px;
  background: var(--accent);
  z-index: 25;
  transition: width 0.1s linear;
}

.back {
  font-size: 0.84rem;
  color: var(--ink-mute);
  letter-spacing: 0.08em;
  transition: color 0.22s ease;
}

.back:hover {
  color: var(--accent);
}

.detail-head {
  max-width: 780px;
  margin: 40px auto 0;
}

.detail-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.detail-tags span {
  font-size: 0.7rem;
  letter-spacing: 0.16em;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 4px 14px;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.detail-head h1 {
  font-size: 2.3rem;
  line-height: 1.42;
  margin: 0 0 28px;
  letter-spacing: 0.03em;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
}

.detail-meta img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.detail-meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-meta strong {
  font-size: 0.9rem;
  font-weight: 500;
}

.detail-meta span {
  font-size: 0.74rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.06em;
}

.detail-cover {
  max-width: 960px;
  margin: 46px auto 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.detail-cover img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.detail-body {
  max-width: var(--reading-width);
  margin: 56px auto 0;
}

.detail-body .lead {
  font-size: 0.96rem;
  color: var(--ink-soft);
  border-left: 3px solid var(--accent);
  padding-left: 20px;
  margin: 0 0 44px;
}

.detail-body h2 {
  font-size: 1.28rem;
  margin: 52px 0 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.detail-body p {
  margin: 0 0 26px;
  font-size: 1rem;
  color: #33373d;
  text-align: justify;
}

.detail-body :deep(code) {
  font-family: "SFMono-Regular", Menlo, Consolas, monospace;
  font-size: 0.86em;
  background: var(--accent-soft);
  color: #5f5445;
  padding: 2px 7px;
  border-radius: 5px;
}

.detail-tail {
  max-width: var(--reading-width);
  margin: 70px auto 0;
  padding: 30px 34px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  flex-wrap: wrap;
}

.tail-note strong {
  font-size: 0.92rem;
}

.tail-note p {
  margin: 10px 0 0;
  font-size: 0.84rem;
  color: var(--ink-mute);
  max-width: 360px;
}

.btn {
  border: 1px solid var(--accent);
  border-radius: 999px;
  padding: 11px 26px;
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  color: var(--accent);
  background: transparent;
  transition: all 0.22s ease;
  white-space: nowrap;
}

.btn:hover {
  background: var(--accent);
  color: #fff;
}

.related {
  margin-top: 92px;
  padding-top: 40px;
  border-top: 1px solid var(--line);
}

.related h2 {
  font-size: 1.5rem;
  margin: 12px 0 30px;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 26px;
}

.not-found {
  text-align: center;
  padding: 120px 0;
}

.not-found h1 {
  font-size: 1.7rem;
  margin-bottom: 14px;
}

.not-found p {
  color: var(--ink-mute);
  font-size: 0.88rem;
  margin-bottom: 30px;
}

@media (max-width: 900px) {
  .progress {
    top: 64px;
  }
  .detail-head h1 {
    font-size: 1.8rem;
  }
  .related-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail {
    padding-top: 32px;
  }
  .detail-head h1 {
    font-size: 1.5rem;
  }
  .detail-body {
    margin-top: 40px;
  }
  .detail-body h2 {
    font-size: 1.14rem;
    margin-top: 40px;
  }
  .detail-tail {
    padding: 24px;
  }
  .related {
    margin-top: 66px;
  }
}
</style>
