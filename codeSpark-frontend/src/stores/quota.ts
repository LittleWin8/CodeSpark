import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getMyQuota } from '@/api/userQuotaUsageController'
import { useLoginUserStore } from '@/stores/loginUser'

/**
 * 当前登录用户的额度状态（单一数据源）
 * 头部额度环、个人中心、生成完成后的刷新都读写这里，避免多处重复请求与状态不同步
 */
export const useQuotaStore = defineStore('quota', () => {
  // 后端返回的额度原始数据
  const info = ref<API.QuotaInfoVO>()

  // 是否已完成首次加载
  const loaded = ref(false)

  // 是否展示额度环：登录即常驻展示（数据未加载时显示占位态）
  const visible = computed(() => Boolean(useLoginUserStore().loginUser.id))

  // 剩余百分比（0~100，向下取整；用于环形进度与颜色分级）
  // 未加载/不限额时按 100 处理（环上显示 ∞ 占位）
  const remainingPercent = computed(() => {
    const q = info.value
    if (!q || q.unlimited || !q.monthlyLimit || q.monthlyLimit <= 0) {
      return 100
    }
    const remaining = q.remainingTokens ?? q.monthlyLimit
    return Math.max(0, Math.min(100, Math.floor((remaining / q.monthlyLimit) * 100)))
  })

  // 颜色等级：剩余 <5% 危险、<20% 警告、其余正常
  const level = computed<'normal' | 'warning' | 'danger'>(() => {
    const p = remainingPercent.value
    if (p < 5) {
      return 'danger'
    }
    if (p < 20) {
      return 'warning'
    }
    return 'normal'
  })

  /**
   * 拉取当前额度（未登录静默跳过；请求失败静默，不打断页面）
   */
  async function fetchQuota() {
    if (!useLoginUserStore().loginUser.id) {
      return
    }
    try {
      const res = await getMyQuota()
      if (res.data.code === 0 && res.data.data) {
        const d = res.data.data
        // 后端 Long 序列化为字符串（防 JS 精度丢失），此处统一归一化为 number，
        // 避免下游比较/除法/格式化踩字符串字典序的坑
        info.value = {
          enabled: Boolean(d.enabled),
          unlimited: Boolean(d.unlimited),
          monthlyLimit: Number(d.monthlyLimit ?? 0),
          usedTokens: Number(d.usedTokens ?? 0),
          remainingTokens: Number(d.remainingTokens ?? -1),
        }
        loaded.value = true
      }
    } catch {
      // 静默：额度数据拉取失败不影响主流程
    }
  }

  /**
   * 登出时清理本地额度数据
   */
  function clear() {
    info.value = undefined
    loaded.value = false
  }

  return { info, loaded, visible, remainingPercent, level, fetchQuota, clear }
})
