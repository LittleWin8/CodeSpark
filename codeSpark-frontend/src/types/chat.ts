/** 聊天消息角色 */
export type ChatRole = 'user' | 'ai'

/** 文本块：AI 正文流 */
export interface TextBlock {
  type: 'text'
  text: string
}

/** 文件写入卡片：由工具事件驱动（writing = 正在写入 / 已写入） */
export interface ToolBlock {
  type: 'tool'
  path: string
  lang?: string
  content?: string
  writing: boolean
  /** 进入"正在写入"态的时间戳（毫秒），用于保证写入态最短可见时长 */
  writingStart?: number
  expanded?: boolean
}

export type MessageBlock = TextBlock | ToolBlock

/** 聊天消息（实时生成 + 历史记录共用） */
export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  files?: string[]
  done?: boolean
  /** 后端创建时间，用于历史消息游标分页 */
  createTime?: string
  /** 是否为历史记录（区分当前会话实时生成的消息） */
  history?: boolean
  /** 内容是否被用户手动折叠 */
  collapsed?: boolean
  /** 模型推理（thinking）流式文本，仅实时生成时存在 */
  thinking?: string
  /** 推理是否结束（第一条正文到达即结束） */
  thinkingDone?: boolean
  /** 思考面板展开态 */
  thinkingExpanded?: boolean
  /** 首片思考到达时间戳（毫秒） */
  thinkingStart?: number
  /** 思考总耗时（秒），推理结束时冻结，避免完成后数字继续跳动 */
  thinkingElapsed?: number
  /** AI 消息的渲染块（文本块与文件写入卡片按流式顺序排列） */
  blocks?: MessageBlock[]
  /** 工具轮之间的"规划下一步"占位状态（模型静默推理 + API 往返期间亮起） */
  planningNext?: boolean
}
