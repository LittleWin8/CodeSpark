<script setup>
import { computed } from 'vue'
import { articles } from '@/utils/articles'

const sorted = computed(() =>
  [...articles].sort((a, b) => (a.date < b.date ? 1 : -1))
)

const groups = computed(() => {
  const map = new Map()
  sorted.value.forEach((item) => {
    const year = item.date.slice(0, 4)
    if (!map.has(year)) map.set(year, [])
    map.get(year).push(item)
  })
  return [...map.entries()].map(([year, list]) => ({ year, list }))
})

const totalMinutes = computed(() => articles.reduce((sum, item) => sum + item.readingTime, 0))
</script>

<template>
  <div class="archive container">
    <header class="archive-head">
      <p class="section-label">Archive</p>
      <h1>归档</h1>
      <p class="archive-desc">
        按年份整理的全部文章，共 {{ articles.length }} 篇，约 {{ totalMinutes }} 分钟阅读时间。
      </p>
    </header>

    <section v-for="group in groups" :key="group.year" class="year-block">
      <div class="year-head">
        <strong>{{ group.year }}</strong>
        <span>{{ group.list.length }} 篇</span>
      </div>

      <ul class="entry-list">
        <li v-for="item in group.list" :key="item.id" class="entry">
          <router-link :to="`/article/${item.id}`" class="entry-link">
            <span class="entry-date">{{ item.date.slice(5).replace('-', ' / ') }}</span>
            <span class="entry-title">{{ item.title }}</span>
            <span class="entry-tags">
              <em v-for="tag in item.tags" :key="tag">{{ tag }}</em>
            </span>
            <span class="entry-time">{{ item.readingTime }} 分钟</span>
          </router-link>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.archive {
  padding-top: 64px;
  padding-bottom: 96px;
}

.archive-head h1 {
  font-size: 2.2rem;
  margin: 16px 0 18px;
  letter-spacing: 0.06em;
}

.archive-desc {
  font-size: 0.88rem;
  color: var(--ink-mute);
  margin: 0;
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.04em;
}

.year-block {
  margin-top: 54px;
}

.year-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.year-head strong {
  font-size: 1.5rem;
  font-weight: 500;
  letter-spacing: 0.1em;
}

.year-head span {
  font-size: 0.72rem;
  color: var(--ink-mute);
  letter-spacing: 0.2em;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.entry-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.entry + .entry {
  border-top: 1px dashed var(--line);
}

.entry-link {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 20px 8px;
  transition: background 0.22s ease;
}

.entry-link:hover {
  background: var(--surface);
}

.entry-link:hover .entry-title {
  color: var(--accent);
}

.entry-date {
  flex: 0 0 74px;
  font-size: 0.74rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.08em;
}

.entry-title {
  flex: 1;
  font-size: 1rem;
  color: var(--ink);
  transition: color 0.22s ease;
}

.entry-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.entry-tags em {
  font-style: normal;
  font-size: 0.68rem;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 3px 12px;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.entry-time {
  flex: 0 0 76px;
  text-align: right;
  font-size: 0.72rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.08em;
}

@media (max-width: 720px) {
  .archive {
    padding-top: 40px;
  }
  .archive-head h1 {
    font-size: 1.7rem;
  }
  .entry-link {
    flex-wrap: wrap;
    gap: 10px 16px;
    padding: 18px 4px;
  }
  .entry-date {
    flex: 0 0 auto;
  }
  .entry-title {
    flex: 1 1 100%;
    order: -1;
    font-size: 0.96rem;
  }
  .entry-time {
    flex: 0 0 auto;
  }
}
</style>
