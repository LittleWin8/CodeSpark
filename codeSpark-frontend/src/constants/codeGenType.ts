import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * 代码生成类型枚举
 * 与后端 CodeGenTypeEnum.java 对应
 */
export enum CodeGenTypeEnum {
  /** 原生 HTML 模式 */
  HTML = 'html',
  /** 原生多文件模式（HTML + CSS + JS） */
  MULTI_FILE = 'multi_file',
  /** VUE 工程模式（带工具调用） */
  VUE_PROJECT = 'vue',
}

/**
 * 代码生成类型的展示工具（多语言）
 * 统一管理选项、标签、颜色，避免各页面重复定义
 */
export function useCodeGenType() {
  const { t } = useI18n()

  /** 下拉框/单选选项 */
  const options = computed(() => [
    { label: t('appManage.typeHtml'), value: CodeGenTypeEnum.HTML },
    { label: t('appManage.typeMultiFile'), value: CodeGenTypeEnum.MULTI_FILE },
    { label: t('appManage.typeVue'), value: CodeGenTypeEnum.VUE_PROJECT },
  ])

  /** 根据值获取显示标签，未知值返回原值或 '-' */
  const label = (value?: string): string =>
    options.value.find((o) => o.value === value)?.label || value || '-'

  /** 根据值获取标签颜色 */
  const color = (value?: string): string => {
    switch (value) {
      case CodeGenTypeEnum.HTML:
        return 'blue'
      case CodeGenTypeEnum.MULTI_FILE:
        return 'orange'
      case CodeGenTypeEnum.VUE_PROJECT:
        return 'purple'
      default:
        return 'default'
    }
  }

  return { options, label, color }
}
