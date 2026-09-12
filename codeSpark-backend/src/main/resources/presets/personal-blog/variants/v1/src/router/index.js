import { createRouter, createWebHashHistory } from 'vue-router'
import Home from '@/pages/Home.vue'
import ArticleDetail from '@/pages/ArticleDetail.vue'
import About from '@/pages/About.vue'
import Archive from '@/pages/Archive.vue'
import NotFound from '@/pages/NotFound.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: Home
  },
  {
    path: '/article/:id',
    name: 'article',
    component: ArticleDetail
  },
  {
    path: '/about',
    name: 'about',
    component: About
  },
  {
    path: '/archive',
    name: 'archive',
    component: Archive
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFound
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

export default router
