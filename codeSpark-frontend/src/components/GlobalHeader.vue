<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">{{ t('header.title') }}</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：语言切换 + 用户操作 -->
      <a-col>
        <a-space :size="16">
          <LanguageSwitcher />
          <div class="user-login-status">
            <div v-if="loginUserStore.loginUser.id">
              <a-dropdown>
                <a-space>
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                  {{ loginUserStore.loginUser.userName ?? t('header.anonymous') }}
                </a-space>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="doLogout">
                      <LogoutOutlined />
                      {{ t('common.logout') }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <div v-else>
              <a-button class="login-btn" type="primary" href="/user/login">{{ t('common.login') }}</a-button>
            </div>
          </div>
        </a-space>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { MenuProps } from 'ant-design-vue'
import { AppstoreOutlined, HomeOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getErrorMessage } from '@/utils/errorMessage'
import LanguageSwitcher from './LanguageSwitcher.vue'
import { message } from 'ant-design-vue'
import { userLogout } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import checkAccess from '@/access/checkAccess.ts'
import ACCESS_ENUM from '@/access/accessEnum.ts'

const loginUserStore = useLoginUserStore()
const { t } = useI18n()
const router = useRouter()

// 用户注销
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: t('header.anonymous'),
    })
    message.success(t('header.logoutSuccess'))
    await router.push('/user/login')
  } else {
    message.error(t('header.logoutFailed') + getErrorMessage(res.data.code, res.data.message))
  }
}

// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])

// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项（含权限元数据）
interface MenuItem {
  key: string
  icon?: () => ReturnType<typeof h>
  label: string
  title: string
  access?: string
}

const originItems = computed<MenuItem[]>(() => [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: t('nav.home'),
    title: t('nav.home'),
  },
  {
    key: '/admin/userManage',
    icon: () => h(UserOutlined),
    label: t('nav.userManage'),
    title: t('nav.userManage'),
    access: ACCESS_ENUM.ADMIN,
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: t('nav.appManage'),
    title: t('nav.appManage'),
    access: ACCESS_ENUM.ADMIN,
  },
])

// 根据权限过滤菜单项
const filterMenus = (items: MenuItem[]): MenuProps['items'] => {
  return items
    .filter((item) => {
      if (item.access) {
        return checkAccess(loginUserStore.loginUser, item.access)
      }
      return true
    })
    .map((item) => ({
      key: item.key,
      icon: item.icon,
      label: item.label,
      title: item.title,
      style: { minWidth: '90px' },
    }))
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems.value))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  if (key.startsWith('/')) {
    router.push(key)
  }
}
</script>

<style scoped>
.header {
  background: linear-gradient(90deg, #dff3f5 0%, #f4fafa 100%);
  padding: 0 24px;
  border-bottom: 1px solid rgba(31, 143, 122, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.site-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1d1d1f;
  background: linear-gradient(135deg, #1f8f7a 0%, #4ab8a4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.ant-menu-horizontal {
  border-bottom: none !important;
  background: transparent !important;
}

:deep(.ant-menu-horizontal > .ant-menu-item) {
  color: #4b5563;
}

:deep(.ant-menu-horizontal > .ant-menu-item-selected) {
  color: #1f8f7a;
  border-bottom-color: #1f8f7a;
}

:deep(.ant-menu-horizontal > .ant-menu-item:hover) {
  color: #1f8f7a;
}

.login-btn {
  min-width: 90px;
  background: linear-gradient(135deg, #1f8f7a 0%, #4ab8a4 100%);
  border: none;
  border-radius: 8px;
}
</style>
