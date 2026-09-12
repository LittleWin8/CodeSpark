<script setup>
defineProps({
  article: {
    type: Object,
    required: true
  }
})
</script>

<template>
  <router-link :to="`/article/${article.id}`" class="card">
    <div class="card-cover">
      <img :src="article.cover" :alt="article.title" loading="lazy" />
    </div>
    <div class="card-body">
      <div class="card-meta">
        <span>{{ article.date }}</span>
        <span class="dot">·</span>
        <span>{{ article.readingTime }} 分钟</span>
      </div>
      <h3>{{ article.title }}</h3>
      <p>{{ article.summary }}</p>
      <div class="card-foot">
        <span v-for="tag in article.tags" :key="tag" class="tag">{{ tag }}</span>
        <em>阅读 →</em>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  height: 100%;
  transition: transform 0.28s ease, box-shadow 0.28s ease, border-color 0.28s ease;
}

.card:hover {
  transform: translateY(-4px);
  border-color: #ded8cd;
  box-shadow: 0 34px 46px -42px rgba(35, 38, 43, 0.95);
}

.card-cover {
  overflow: hidden;
  background: #eee;
}

.card-cover img {
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  transition: transform 0.6s ease;
}

.card:hover .card-cover img {
  transform: scale(1.05);
}

.card-body {
  padding: 24px 24px 26px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-meta {
  display: flex;
  gap: 8px;
  font-size: 0.72rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 0.06em;
}

.card-meta .dot {
  opacity: 0.6;
}

.card-body h3 {
  font-size: 1.08rem;
  line-height: 1.62;
  margin: 14px 0 12px;
  transition: color 0.24s ease;
}

.card:hover .card-body h3 {
  color: var(--accent);
}

.card-body p {
  font-size: 0.84rem;
  color: var(--ink-soft);
  margin: 0 0 22px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  font-size: 0.68rem;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 3px 11px;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.card-foot em {
  margin-left: auto;
  font-style: normal;
  font-size: 0.76rem;
  color: var(--ink-mute);
  letter-spacing: 0.08em;
}

@media (max-width: 640px) {
  .card-body {
    padding: 20px 20px 22px;
  }
  .card-body h3 {
    font-size: 1rem;
  }
}
</style>
