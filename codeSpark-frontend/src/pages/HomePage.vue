<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  CloseOutlined,
  EyeOutlined,
  PaperClipOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import AppCard from '@/components/AppCard.vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getErrorMessage } from '@/utils/errorMessage'
import { getDeployUrl, getPreviewUrl } from '@/utils/url'
import ACCESS_ENUM from '@/access/accessEnum'

const { t } = useI18n()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const prompt = ref('')
const creating = ref(false)

const suggestKeys = [
  'suggestPopEcommerce',
  'suggestEnterprise',
  'suggestAdmin',
  'suggestCommunity',
] as const

const suggestions = computed(() => suggestKeys.map((key) => t(`home.${key}`)))

const isLoggedIn = computed(() => {
  const role = loginUserStore.loginUser.userRole
  return Boolean(loginUserStore.loginUser.id) && role !== ACCESS_ENUM.NOT_LOGIN
})

const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  appName: '',
  sortField: '"createTime"',
  sortOrder: 'descend',
})

const featuredApps = ref<API.AppVO[]>([])
const featuredTotal = ref(0)
const featuredQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  appName: '',
  sortField: '"createTime"',
  sortOrder: 'descend',
})

const fetchMyApps = async () => {
  if (!isLoggedIn.value) {
    myApps.value = []
    myTotal.value = 0
    return
  }
  const res = await listMyAppVoByPage({ ...myQuery })
  if (res.data.code === 0 && res.data.data) {
    myApps.value = res.data.data.records ?? []
    myTotal.value = res.data.data.totalRow ?? 0
  } else {
    message.error(getErrorMessage(res.data.code, res.data.message))
  }
}

const fetchFeaturedApps = async () => {
  const res = await listGoodAppVoByPage({ ...featuredQuery })
  if (res.data.code === 0 && res.data.data) {
    featuredApps.value = res.data.data.records ?? []
    featuredTotal.value = res.data.data.totalRow ?? 0
  } else {
    message.error(getErrorMessage(res.data.code, res.data.message))
  }
}

const onMyPageChange = (page: number) => {
  myQuery.pageNum = page
  fetchMyApps()
}

const onFeaturedPageChange = (page: number) => {
  featuredQuery.pageNum = page
  fetchFeaturedApps()
}

const applySuggestion = (text: string) => {
  prompt.value = text
}

const handleUpload = () => {
  message.info(t('home.uploadTip'))
}

const handleOptimize = () => {
  const value = prompt.value.trim()
  if (!value) {
    message.warning(t('home.optimizeTip'))
    return
  }
  if (!value.includes(t('home.optimizeSuffix').trim())) {
    prompt.value = `${value}${t('home.optimizeSuffix')}`
  }
}

const createApp = async () => {
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning(t('home.promptRequired'))
    return
  }
  if (!isLoggedIn.value) {
    message.warning(t('home.loginRequired'))
    await router.push(`/user/login?redirect=${encodeURIComponent('/')}`)
    return
  }

  creating.value = true
  try {
    const res = await addApp({ initPrompt })
    if (res.data.code === 0 && res.data.data) {
      // 对话页会根据「自己的 app 且没有对话历史」自动发送初始消息；
      // 携带 from=home：对话页返回时直接回首页
      await router.push({ path: `/app/chat/${res.data.data}`, query: { from: 'home' } })
    } else {
      message.error(getErrorMessage(res.data.code, res.data.message) || t('home.createFailed'))
    }
  } finally {
    creating.value = false
  }
}

const openApp = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  router.push({ path: `/app/chat/${app.id}`, query: { from: 'home' } })
}

/**
 * 查看作品：仅打开已部署地址
 */
const openAppWork = (app?: API.AppVO | null) => {
  if (!app?.deployKey) {
    return
  }
  const url = getDeployUrl(app.deployKey)
  if (url) {
    window.open(url, '_blank')
  }
}

/**
 * 我的作品：未部署时打开本地生成预览
 */
const openMyAppWork = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  if (app.deployKey) {
    openAppWork(app)
  } else {
    const url = getPreviewUrl(app.codeGenType, app.id)
    if (url) {
      window.open(url, '_blank')
    }
  }
}

// 精选应用预览大卡片
const previewApp = ref<API.AppVO>()
const previewVisible = ref(false)
const previewUrl = computed(() =>
  getPreviewUrl(previewApp.value?.codeGenType, previewApp.value?.id),
)

const previewAuthor = computed(() => previewApp.value?.user?.userName || t('home.official'))

const previewDate = computed(() => {
  if (!previewApp.value?.createTime) {
    return ''
  }
  return dayjs(previewApp.value.createTime).format('YYYY-MM-DD')
})

const openPreview = (app: API.AppVO) => {
  previewApp.value = app
  previewVisible.value = true
  document.body.style.overflow = 'hidden'
}

const closePreview = () => {
  previewVisible.value = false
  document.body.style.overflow = ''
}

const onPreviewKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && previewVisible.value) {
    closePreview()
  }
}

watch(isLoggedIn, () => {
  fetchMyApps()
})

onMounted(() => {
  fetchMyApps()
  fetchFeaturedApps()
  window.addEventListener('keydown', onPreviewKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onPreviewKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <div id="homePage">
    <section class="hero">
      <div class="hero__inner">
        <div class="hero__head">
          <img class="hero__logo" src="@/assets/logo.png" alt="CodeSpark" />
          <div class="hero__head-text">
            <h1 class="hero__title">{{ t('home.heroTitle') }}</h1>
            <p class="hero__subtitle">{{ t('home.heroSubtitle') }}</p>
          </div>
        </div>

        <div class="prompt-box">
          <a-textarea
            v-model:value="prompt"
            :placeholder="t('home.promptPlaceholder')"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :bordered="false"
            @pressEnter.exact.prevent="createApp"
          />
          <div class="prompt-box__toolbar">
            <a-space>
              <a-button type="text" @click="handleUpload">
                <template #icon><PaperClipOutlined /></template>
                {{ t('common.upload') }}
              </a-button>
              <a-button type="text" @click="handleOptimize">
                <template #icon><ThunderboltOutlined /></template>
                {{ t('common.optimize') }}
              </a-button>
            </a-space>
            <a-button
              class="prompt-box__send"
              type="primary"
              shape="circle"
              :loading="creating"
              @click="createApp"
            >
              <template #icon><ArrowUpOutlined /></template>
            </a-button>
          </div>
        </div>

        <div class="suggestions">
          <button
            v-for="item in suggestions"
            :key="item"
            type="button"
            class="suggestion-tag"
            @click="applySuggestion(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </section>

    <section class="lists">
      <div class="lists__inner">
        <div class="section-block">
          <div class="section-header">
            <h2>{{ t('home.myApps') }}</h2>
            <a-input-search
              v-if="isLoggedIn"
              v-model:value="myQuery.appName"
              :placeholder="t('home.searchByName')"
              style="max-width: 240px"
              allow-clear
              @search="
                () => {
                  myQuery.pageNum = 1
                  fetchMyApps()
                }
              "
            />
          </div>

          <a-empty v-if="!isLoggedIn" :description="t('home.loginToViewMyApps')" />
          <a-empty v-else-if="!myApps.length" :description="t('home.noMyApps')" />
          <div v-else class="app-grid">
            <AppCard
              v-for="app in myApps"
              :key="app.id"
              :app="app"
              variant="mine"
              @click="openApp(app)"
              @view-chat="openApp(app)"
              @view-work="openMyAppWork(app)"
            />
          </div>
          <div v-if="isLoggedIn && myTotal > myQuery.pageSize!" class="pagination-wrap">
            <a-pagination
              :current="myQuery.pageNum"
              :page-size="myQuery.pageSize"
              :total="myTotal"
              :show-size-changer="false"
              @change="onMyPageChange"
            />
          </div>
        </div>

        <div class="section-block">
          <div class="section-header">
            <h2>{{ t('home.featuredApps') }}</h2>
            <a-input-search
              v-model:value="featuredQuery.appName"
              :placeholder="t('home.searchByName')"
              style="max-width: 240px"
              allow-clear
              @search="
                () => {
                  featuredQuery.pageNum = 1
                  fetchFeaturedApps()
                }
              "
            />
          </div>

          <a-empty v-if="!featuredApps.length" :description="t('home.noFeaturedApps')" />
          <div v-else class="app-grid">
            <AppCard
              v-for="app in featuredApps"
              :key="app.id"
              :app="app"
              variant="featured"
              @preview="openPreview(app)"
              @click="openPreview(app)"
            />
          </div>
          <div v-if="featuredTotal > featuredQuery.pageSize!" class="pagination-wrap">
            <a-pagination
              :current="featuredQuery.pageNum"
              :page-size="featuredQuery.pageSize"
              :total="featuredTotal"
              :show-size-changer="false"
              @change="onFeaturedPageChange"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- 精选应用预览大卡片 -->
    <Teleport to="body">
      <div v-if="previewVisible" class="preview-overlay" @click.self="closePreview">
        <div class="preview-card" role="dialog" aria-modal="true">
          <div class="preview-card__header">
            <div class="preview-card__info">
              <h3 class="preview-card__title">
                {{ previewApp?.appName || t('appChat.previewTitle') }}
              </h3>
              <div class="preview-card__meta">
                <span v-if="previewDate">{{ previewDate }}</span>
                <span>{{ t('home.previewBy', { name: previewAuthor }) }}</span>
              </div>
            </div>
            <div class="preview-card__actions">
              <a-button
                v-if="previewApp?.deployKey"
                class="preview-card__view-work"
                @click="openAppWork(previewApp)"
              >
                <template #icon><EyeOutlined /></template>
                {{ t('common.viewWork') }}
              </a-button>
              <button type="button" class="preview-card__close" @click="closePreview">
                <CloseOutlined />
              </button>
            </div>
          </div>

          <div class="preview-card__body">
            <iframe
              v-if="previewUrl"
              :src="previewUrl"
              class="preview-card__iframe"
              :title="t('appChat.previewTitle')"
            />
            <a-empty v-else :description="t('appChat.previewEmpty')" />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
#homePage {
  min-height: calc(100vh - 64px);
  padding-bottom: 72px;
}

.hero {
  background: linear-gradient(180deg, #d4f0f2 0%, #eef8f8 42%, #f5f5f7 100%);
  padding: 56px 24px 40px;
}

.hero__inner {
  max-width: 880px;
  margin: 0 auto;
  text-align: center;
}

.hero__title {
  margin: 0;
  font-size: 36px;
  font-weight: 700;
  color: #111;
  letter-spacing: 1px;
}

.hero__head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 32px;
}

.hero__head-text {
  text-align: left;
}

.hero__logo {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.hero__subtitle {
  margin: 6px 0 0;
  color: #8a8a8e;
  font-size: 15px;
}

.prompt-box {
  background: #fff;
  border-radius: 24px;
  padding: 20px 20px 14px;
  box-shadow: 0 10px 40px rgba(31, 84, 144, 0.08);
  text-align: left;
}

.prompt-box :deep(textarea) {
  resize: none;
  font-size: 16px;
  padding: 0;
}

.prompt-box__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.prompt-box__send {
  width: 40px;
  height: 40px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.suggestion-tag {
  border: none;
  background: rgba(255, 255, 255, 0.72);
  color: #4b5563;
  border-radius: 999px;
  padding: 8px 16px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.suggestion-tag:hover {
  background: #fff;
  color: #111;
}

.lists {
  padding: 8px 24px 48px;
}

.lists__inner {
  max-width: 1120px;
  margin: 0 auto;
  background: #fff;
  border-radius: 24px;
  padding: 32px 28px 40px;
}

.section-block + .section-block {
  margin-top: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #111;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .hero__title {
    font-size: 32px;
  }

  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
  }
}

@media (max-width: 640px) {
  .hero__title {
    font-size: 26px;
  }

  .hero__head {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }

  .hero__head-text {
    text-align: center;
  }

  .hero__logo {
    width: 40px;
    height: 40px;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
/* 预览层挂到 body，需非 scoped */
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.45);
  box-sizing: border-box;
}

.preview-card {
  position: relative;
  width: min(1440px, 96vw);
  height: min(920px, calc(100vh - 32px));
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.preview-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 16px;
  flex-shrink: 0;
}

.preview-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.preview-card__close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #f3f4f6;
  color: #4b5563;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.preview-card__close:hover {
  background: #e5e7eb;
}

.preview-card__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #111;
  line-height: 1.3;
}

.preview-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 13px;
  color: #8c8c8c;
}

.preview-card__view-work {
  height: 36px !important;
  border-radius: 999px !important;
  background: #f3f4f6 !important;
  border: none !important;
  color: #374151 !important;
  box-shadow: none !important;
  display: inline-flex !important;
  align-items: center !important;
}

.preview-card__view-work:hover {
  background: #e5e7eb !important;
  color: #111 !important;
}

.preview-card__body {
  flex: 1;
  min-height: 0;
  margin: 0 24px 24px;
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #eef0f3;
}

.preview-card__iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}
</style>
