<script setup>
import { computed, ref } from 'vue'
import ArticleCard from '@/components/ArticleCard.vue'
import TagFilter from '@/components/TagFilter.vue'
import { articles, tags, author } from '@/utils/articles'

const activeTag = ref('全部')
const keyword = ref('')
const subscribed = ref(false)
const email = ref('')

const featured = computed(() => articles.find((item) => item.featured) || articles[0])

const filtered = computed(() => {
  return articles.filter((item) => {
    const tagOk = activeTag.value === '全部' || item.tags.includes(activeTag.value)
    const key = keyword.value.trim()
    const keyOk =
      !key || item.title.includes(key) || item.summary.includes(key) || item.tags.join('').includes(key)
    return tagOk && keyOk
  })
})

const topics = [
  {
    name: '前端工程',
    desc: '构建、规范、部署与协作方式',
    count: articles.filter((a) => a.tags.includes('前端工程')).length,
    cover: 'https://picsum.photos/seed/{{imgSeed}}-topic-eng/600/400'
  },
  {
    name: '组件设计',
    desc: '拆分边界、复用与状态管理',
    count: articles.filter((a) => a.tags.includes('组件设计')).length,
    cover: 'https://picsum.photos/seed/{{imgSeed}}-topic-comp/600/400'
  },
  {
    name: '阅读笔记',
    desc: '读书之后真正留下来的东西',
    count: articles.filter((a) => a.tags.includes('阅读笔记')).length,
    cover: 'https://picsum.photos/seed/{{imgSeed}}-topic-read/600/400'
  }
]

function pickTag(tag) {
  activeTag.value = tag
  scrollToGrid()
}

function scrollToGrid() {
  setTimeout(() => {
    const el = document.getElementById('latest')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 40)
}

function subscribe() {
  if (!email.value.trim()) return
  subscribed.value = true
  email.value = ''
}

const stats = [
  { value: articles.length, label: '篇文章' },
  { value: '4.2', label: '万字积累' },
  { value: '2', label: '年更新' }
]
</script>

<template>
  <div class="home">
    <section class="hero container">
      <div class="hero-text">
        <p class="section-label">A Personal Journal</p>
        <h1>
          把想清楚的事<br />
          写下来
        </h1>
        <p class="hero-desc">
          我是{{ author.name }}，一名前端工程师。这里记录我在工程实践、组件设计和日常阅读中的思考，
          不追热点，只写那些过一年再看依然成立的东西。
        </p>
        <div class="hero-actions">
          <button class="btn primary" type="button" @click="scrollToGrid">开始阅读</button>
          <router-link class="btn ghost" to="/about">关于我</router-link>
        </div>
        <div class="stats">
          <div v-for="item in stats" :key="item.label">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="hero-side">
        <div class="hero-photo">
          <img :src="author.avatar" :alt="author.name" />
        </div>
        <div class="hero-note">
          <span class="note-label">本周手记</span>
          <p>重写了两篇旧稿，删掉四百多字。删掉的部分其实都不是废话，只是挡住了重点。</p>
          <span class="note-date">2024 / 06 / 21</span>
        </div>
      </div>
    </section>

    <section class="featured container">
      <div class="featured-head">
        <p class="section-label">Featured</p>
        <h2>本期推荐</h2>
      </div>
      <router-link :to="`/article/${featured.id}`" class="feature-box">
        <div class="feature-media">
          <img :src="featured.cover" :alt="featured.title" />
        </div>
        <div class="feature-body">
          <div class="feature-meta">
            <span>{{ featured.date }}</span>
            <span>·</span>
            <span>{{ featured.readingTime }} 分钟阅读</span>
          </div>
          <h3>{{ featured.title }}</h3>
          <p>{{ featured.summary }}</p>
          <div class="feature-tags">
            <span v-for="tag in featured.tags" :key="tag">{{ tag }}</span>
          </div>
          <span class="feature-link">阅读全文 →</span>
        </div>
      </router-link>
    </section>

    <section class="topics container">
      <div class="topics-head">
        <p class="section-label">Topics</p>
        <h2>按主题浏览</h2>
      </div>
      <div class="topic-grid">
        <button v-for="topic in topics" :key="topic.name" class="topic-card" type="button" @click="pickTag(topic.name)">
          <img :src="topic.cover" :alt="topic.name" loading="lazy" />
          <div class="topic-mask">
            <strong>{{ topic.name }}</strong>
            <span>{{ topic.desc }}</span>
            <em>{{ topic.count }} 篇</em>
          </div>
        </button>
      </div>
    </section>

    <section id="latest" class="latest container">
      <div class="latest-head">
        <div>
          <p class="section-label">Archive</p>
          <h2>全部文章</h2>
        </div>
        <input v-model="keyword" class="search" type="search" placeholder="搜索标题、摘要或标签" />
      </div>

      <TagFilter :tags="tags" :active="activeTag" @select="activeTag = $event" />

      <p class="result-line">
        共 <strong>{{ filtered.length }}</strong> 篇 · 当前筛选「{{ activeTag }}」
        <span v-if="keyword.trim()"> · 关键词「{{ keyword.trim() }}」</span>
      </p>

      <div v-if="filtered.length" class="article-grid">
        <ArticleCard v-for="item in filtered" :key="item.id" :article="item" />
      </div>
      <div v-else class="empty">
        <strong>没有匹配的文章</strong>
        <p>试着换个关键词，或者点击上面的标签看看其他内容。</p>
        <button class="btn ghost" type="button" @click="activeTag = '全部'; keyword = ''">
          重置筛选
        </button>
      </div>
    </section>

    <section class="subscribe container">
      <div class="subscribe-box">
        <div>
          <p class="section-label">Newsletter</p>
          <h2>订阅更新</h2>
          <p class="subscribe-desc">每两周一封邮件，只包含新文章与一句近况。随时可以退订。</p>
        </div>
        <form class="subscribe-form" @submit.prevent="subscribe">
          <input v-model="email" type="email" placeholder="你的邮箱地址" />
          <button type="submit">订阅</button>
        </form>
        <p v-if="subscribed" class="subscribe-ok">已记录你的邮箱，下一期会准时送达。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 72px;
  padding-top: 88px;
  padding-bottom: 96px;
  align-items: center;
}

.hero-text h1 {
  font-size: 3.4rem;
  line-height: 1.28;
  margin: 22px 0 26px;
  letter-spacing: 0.04em;
}

.hero-desc {
  font-size: 0.96rem;
  color: var(--ink-soft);
  max-width: 520px;
  margin: 0 0 34px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.btn {
  border-radius: 999px;
  padding: 13px 30px;
  font-size: 0.86rem;
  letter-spacing: 0.1em;
  border: 1px solid transparent;
  transition: all 0.24s ease;
  display: inline-block;
}

.btn.primary {
  background: var(--accent);
  color: #fff;
}

.btn.primary:hover {
  background: #6a5e4c;
  transform: translateY(-2px);
}

.btn.ghost {
  border-color: var(--line);
  background: var(--surface);
  color: var(--ink-soft);
}

.btn.ghost:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.stats {
  display: flex;
  gap: 54px;
  margin-top: 58px;
  padding-top: 30px;
  border-top: 1px solid var(--line);
}

.stats div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stats strong {
  font-size: 1.7rem;
  font-weight: 500;
}

.stats span {
  font-size: 0.72rem;
  color: var(--ink-mute);
  letter-spacing: 0.2em;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.hero-photo {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #eee;
}

.hero-photo img {
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
}

.hero-note {
  background: var(--surface);
  border: 1px solid var(--line);
  border-left: 3px solid var(--accent);
  border-radius: 10px;
  padding: 20px 22px;
}

.note-label {
  font-size: 0.7rem;
  letter-spacing: 0.24em;
  color: var(--accent);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.hero-note p {
  margin: 12px 0 12px;
  font-size: 0.86rem;
  color: var(--ink-soft);
}

.note-date {
  font-size: 0.7rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.featured {
  padding-bottom: 96px;
}

.featured-head,
.topics-head {
  margin-bottom: 34px;
}

.featured-head h2,
.topics-head h2,
.latest-head h2,
.subscribe-box h2 {
  font-size: 1.7rem;
  margin: 14px 0 0;
  letter-spacing: 0.05em;
}

.feature-box {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.3s ease, transform 0.3s ease;
}

.feature-box:hover {
  transform: translateY(-3px);
  box-shadow: 0 40px 60px -50px rgba(35, 38, 43, 0.9);
}

.feature-media img {
  width: 100%;
  height: 100%;
  min-height: 340px;
  object-fit: cover;
}

.feature-body {
  padding: 48px 52px;
  display: flex;
  flex-direction: column;
}

.feature-meta {
  display: flex;
  gap: 10px;
  font-size: 0.74rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.08em;
}

.feature-body h3 {
  font-size: 1.6rem;
  line-height: 1.5;
  margin: 18px 0 18px;
}

.feature-body p {
  font-size: 0.9rem;
  color: var(--ink-soft);
  margin: 0 0 26px;
}

.feature-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.feature-tags span {
  font-size: 0.7rem;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: 999px;
  padding: 3px 12px;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.feature-link {
  margin-top: auto;
  padding-top: 28px;
  font-size: 0.84rem;
  color: var(--accent);
  letter-spacing: 0.08em;
}

.topics {
  padding-bottom: 96px;
}

.topic-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
}

.topic-card {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  padding: 0;
  background: #ddd;
  aspect-ratio: 4 / 3;
}

.topic-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s ease;
}

.topic-card:hover img {
  transform: scale(1.06);
}

.topic-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(28, 30, 34, 0.12) 20%, rgba(28, 30, 34, 0.78) 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 6px;
  padding: 26px;
  text-align: left;
}

.topic-mask strong {
  font-size: 1.12rem;
  letter-spacing: 0.12em;
}

.topic-mask span {
  font-size: 0.8rem;
  opacity: 0.82;
}

.topic-mask em {
  font-style: normal;
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  opacity: 0.7;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.latest {
  padding-bottom: 96px;
  scroll-margin-top: 90px;
}

.latest-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 30px;
}

.search {
  width: 280px;
  max-width: 100%;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 12px 22px;
  background: var(--surface);
  color: var(--ink);
  font-size: 0.84rem;
  outline: none;
  transition: border-color 0.22s ease;
}

.search:focus {
  border-color: var(--accent);
}

.result-line {
  font-size: 0.8rem;
  color: var(--ink-mute);
  margin: 24px 0 26px;
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.04em;
}

.result-line strong {
  color: var(--accent);
}

.article-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 26px;
}

.empty {
  border: 1px dashed var(--line);
  border-radius: 14px;
  padding: 66px 30px;
  text-align: center;
  background: var(--surface);
}

.empty strong {
  font-size: 1.06rem;
  display: block;
  margin-bottom: 10px;
}

.empty p {
  color: var(--ink-mute);
  font-size: 0.86rem;
  margin: 0 0 22px;
}

.subscribe {
  padding-bottom: 20px;
}

.subscribe-box {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  padding: 48px 52px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 34px;
  align-items: center;
}

.subscribe-desc {
  font-size: 0.86rem;
  color: var(--ink-soft);
  margin: 14px 0 0;
}

.subscribe-form {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.subscribe-form input {
  flex: 1;
  min-width: 200px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 13px 22px;
  outline: none;
  background: var(--bg);
  font-size: 0.86rem;
}

.subscribe-form input:focus {
  border-color: var(--accent);
}

.subscribe-form button {
  border: none;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  padding: 13px 34px;
  font-size: 0.86rem;
  letter-spacing: 0.1em;
  transition: background 0.22s ease;
}

.subscribe-form button:hover {
  background: #6a5e4c;
}

.subscribe-ok {
  grid-column: 2;
  margin: 0;
  font-size: 0.8rem;
  color: var(--accent);
}

@media (max-width: 1000px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 48px;
    padding-top: 60px;
    padding-bottom: 70px;
  }
  .hero-photo img {
    aspect-ratio: 16 / 9;
  }
  .hero-text h1 {
    font-size: 2.7rem;
  }
  .article-grid,
  .topic-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .feature-box {
    grid-template-columns: 1fr;
  }
  .feature-media img {
    min-height: 260px;
    aspect-ratio: 16 / 9;
  }
  .feature-body {
    padding: 34px 32px;
  }
  .subscribe-box {
    grid-template-columns: 1fr;
    padding: 36px 32px;
  }
  .subscribe-ok {
    grid-column: 1;
  }
}

@media (max-width: 640px) {
  .hero-text h1 {
    font-size: 2.1rem;
  }
  .stats {
    gap: 30px;
    margin-top: 40px;
  }
  .stats strong {
    font-size: 1.4rem;
  }
  .article-grid,
  .topic-grid {
    grid-template-columns: 1fr;
  }
  .featured,
  .topics,
  .latest {
    padding-bottom: 64px;
  }
  .search {
    width: 100%;
  }
}
</style>
