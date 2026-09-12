<script setup>
import { author, articles } from '@/utils/articles'

const skills = [
  { name: 'Vue 3 / 组合式 API', level: 92 },
  { name: '原生 CSS / 响应式布局', level: 88 },
  { name: '工程化与构建配置', level: 80 },
  { name: '性能与可访问性优化', level: 74 }
]

const timeline = [
  {
    year: '2024',
    title: '开始认真写博客',
    desc: '把零散的笔记整理成完整的文章，坚持两周一更，目前已经写下六篇长文。'
  },
  {
    year: '2023',
    title: '主导一次前端重构',
    desc: '负责把历史项目迁移到新的构建工具与组件体系，页面首屏时间下降了约四成。'
  },
  {
    year: '2021',
    title: '专注组件与设计规范',
    desc: '参与维护团队内部的组件库，第一次意识到「统一的接口」比「聪明的实现」更重要。'
  },
  {
    year: '2019',
    title: '入行做前端',
    desc: '从切图开始，边做边学，第一年里最大的收获是学会了读别人的代码。'
  }
]

const principles = [
  {
    title: '先写清楚，再写好看',
    desc: '一篇文章的价值首先在于它有没有把一件事说明白，修辞永远是第二位的。'
  },
  {
    title: '只写自己验证过的',
    desc: '没用过的工具不推荐，没做过的方案不评价。经验可以有限，但不应该是抄来的。'
  },
  {
    title: '允许观点被推翻',
    desc: '两年前写下的结论，如果今天不成立了，我会在原文里注明，而不是悄悄删掉。'
  }
]
</script>

<template>
  <div class="about container">
    <section class="profile">
      <div class="profile-photo">
        <img :src="author.avatar" :alt="author.name" />
      </div>
      <div class="profile-text">
        <p class="section-label">About Me</p>
        <h1>{{ author.name }}</h1>
        <p class="role">{{ author.role }}</p>
        <p class="intro">
          我在一家产品公司做前端开发，日常工作是写页面、拆组件和收拾历史遗留问题。
          下班之后我喜欢写字，把工作中想明白的方法论、读书时被击中的句子，整理成一篇篇能被人读懂的短文。
        </p>
        <p class="intro">
          这个博客没有广告、没有推荐位，文章也不做摘要营销。我希望它像一个安静的抽屉，
          你需要的时候打开，里面正好有你要找的那一页。
        </p>
        <div class="contact">
          <a href="mailto:hello@jing-shui.dev">hello@jing-shui.dev</a>
          <span>杭州 · 中国</span>
          <span>共 {{ articles.length }} 篇文章</span>
        </div>
      </div>
    </section>

    <section class="block">
      <p class="section-label">Skills</p>
      <h2>在做的事</h2>
      <div class="skill-list">
        <div v-for="item in skills" :key="item.name" class="skill">
          <div class="skill-top">
            <span>{{ item.name }}</span>
            <em>{{ item.level }}%</em>
          </div>
          <div class="skill-track">
            <div class="skill-bar" :style="{ width: item.level + '%' }"></div>
          </div>
        </div>
      </div>
    </section>

    <section class="block">
      <p class="section-label">Experience</p>
      <h2>一点经历</h2>
      <div class="timeline">
        <div v-for="item in timeline" :key="item.year" class="timeline-item">
          <span class="year">{{ item.year }}</span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="block">
      <p class="section-label">Principles</p>
      <h2>写作原则</h2>
      <div class="principle-grid">
        <div v-for="(item, index) in principles" :key="item.title" class="principle">
          <span class="num">0{{ index + 1 }}</span>
          <strong>{{ item.title }}</strong>
          <p>{{ item.desc }}</p>
        </div>
      </div>
    </section>

    <section class="cta">
      <div>
        <h2>有想法想聊聊？</h2>
        <p>无论是技术问题还是写作建议，都非常欢迎写信给我。</p>
      </div>
      <a class="btn" href="mailto:hello@jing-shui.dev">写信给我</a>
    </section>
  </div>
</template>

<style scoped>
.about {
  padding-top: 80px;
}

.profile {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 60px;
  align-items: flex-start;
  padding-bottom: 90px;
  border-bottom: 1px solid var(--line);
}

.profile-photo {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #eee;
}

.profile-photo img {
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
}

.profile-text h1 {
  font-size: 2.2rem;
  margin: 18px 0 10px;
  letter-spacing: 0.06em;
}

.role {
  font-size: 0.8rem;
  letter-spacing: 0.24em;
  color: var(--accent);
  font-family: "Helvetica Neue", Arial, sans-serif;
  margin: 0 0 28px;
}

.intro {
  font-size: 0.94rem;
  color: var(--ink-soft);
  margin: 0 0 20px;
  max-width: 620px;
}

.contact {
  display: flex;
  gap: 26px;
  flex-wrap: wrap;
  margin-top: 34px;
  padding-top: 26px;
  border-top: 1px solid var(--line);
  font-size: 0.82rem;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.contact a {
  color: var(--accent);
}

.block {
  padding: 74px 0 0;
}

.block h2 {
  font-size: 1.6rem;
  margin: 14px 0 34px;
}

.skill-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px 60px;
}

.skill-top {
  display: flex;
  justify-content: space-between;
  font-size: 0.86rem;
  margin-bottom: 12px;
  color: var(--ink-soft);
}

.skill-top em {
  font-style: normal;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
  font-size: 0.78rem;
}

.skill-track {
  height: 5px;
  background: #e8e4dd;
  border-radius: 999px;
  overflow: hidden;
}

.skill-bar {
  height: 100%;
  background: var(--accent);
  border-radius: 999px;
  transition: width 0.8s ease;
}

.timeline {
  border-left: 1px solid var(--line);
  padding-left: 34px;
  display: flex;
  flex-direction: column;
  gap: 38px;
}

.timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 20px;
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: -39px;
  top: 8px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--accent);
}

.year {
  font-size: 0.8rem;
  letter-spacing: 0.14em;
  color: var(--ink-mute);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.timeline-item strong {
  font-size: 1rem;
  font-weight: 500;
}

.timeline-item p {
  margin: 10px 0 0;
  font-size: 0.86rem;
  color: var(--ink-soft);
  max-width: 620px;
}

.principle-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.principle {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  padding: 32px 28px;
}

.num {
  font-size: 0.74rem;
  letter-spacing: 0.2em;
  color: var(--accent);
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.principle strong {
  display: block;
  margin: 16px 0 12px;
  font-size: 1rem;
  font-weight: 500;
}

.principle p {
  margin: 0;
  font-size: 0.85rem;
  color: var(--ink-soft);
}

.cta {
  margin: 90px 0 30px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  padding: 46px 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  flex-wrap: wrap;
}

.cta h2 {
  font-size: 1.42rem;
  margin: 0 0 12px;
}

.cta p {
  margin: 0;
  font-size: 0.86rem;
  color: var(--ink-soft);
}

.btn {
  border: 1px solid var(--accent);
  border-radius: 999px;
  padding: 13px 34px;
  font-size: 0.84rem;
  letter-spacing: 0.1em;
  color: var(--accent);
  transition: all 0.22s ease;
  white-space: nowrap;
}

.btn:hover {
  background: var(--accent);
  color: #fff;
}

@media (max-width: 900px) {
  .profile {
    grid-template-columns: 1fr;
    gap: 32px;
    padding-bottom: 60px;
  }
  .profile-photo {
    max-width: 320px;
  }
  .skill-list {
    grid-template-columns: 1fr;
  }
  .principle-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .about {
    padding-top: 48px;
  }
  .profile-text h1 {
    font-size: 1.7rem;
  }
  .timeline-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .cta {
    padding: 32px 26px;
    margin-top: 66px;
  }
}
</style>
