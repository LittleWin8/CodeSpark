import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import UserLoginPage from '../pages/user/UserLoginPage.vue'
import UserRegisterPage from '../pages/user/UserRegisterPage.vue'
import UserProfilePage from '../pages/user/UserProfilePage.vue'
import UserManagePage from '../pages/admin/UserManagePage.vue'
import AppManagePage from '../pages/admin/AppManagePage.vue'
import ChatHistoryManagePage from '../pages/admin/ChatHistoryManagePage.vue'
import AppChatPage from '../pages/app/AppChatPage.vue'
import AppEditPage from '../pages/app/AppEditPage.vue'
import NoAuthPage from '../pages/NoAuthPage.vue'
import ACCESS_ENUM from '@/access/accessEnum.ts'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
      meta: {
        fullWidth: true,
      },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/user/profile',
      name: '个人中心',
      component: UserProfilePage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/app/chat/:id',
      name: '应用对话',
      component: AppChatPage,
      meta: {
        access: ACCESS_ENUM.USER,
        fullWidth: true,
        hideFooter: true,
      },
    },
    {
      path: '/app/edit/:id',
      name: '编辑应用',
      component: AppEditPage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
        wideContent: true,
      },
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
        wideContent: true,
      },
    },
    {
      path: '/admin/chatHistoryManage',
      name: '对话管理',
      component: ChatHistoryManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
        wideContent: true,
      },
    },
    {
      path: '/noAuth',
      name: '无权限',
      component: NoAuthPage,
    },
  ],
})

export default router
