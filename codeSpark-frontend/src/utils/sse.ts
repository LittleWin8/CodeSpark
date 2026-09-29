export type ToolRequestPayload = {
  /** 工具名：writeFile / readFile / modifyFile / deleteFile / readDir */
  name?: string
  path?: string
}

export type ToolExecutedPayload = {
  /** 工具名：writeFile / readFile / modifyFile / deleteFile / readDir */
  name?: string
  path?: string
  lang?: string
  content?: string
}

export type SseHandlers = {
  onMessage: (chunk: string) => void
  /** 模型推理（thinking）分片：后端以命名事件 event: thinking 推送 */
  onThinking?: (chunk: string) => void
  /** 工具调用前（写入文件请求）：event: tool_request，携带真实文件路径 */
  onToolRequest?: (payload: ToolRequestPayload) => void
  /** 工具执行完成（文件已写入）：event: tool_executed，携带路径与文件内容 */
  onToolExecuted?: (payload: ToolExecutedPayload) => void
  /** 业务错误：后端 GlobalExceptionHandler 对 SSE 请求以 business-error 事件透出（流未开始） */
  onBusinessError?: (payload: SseBusinessErrorPayload) => void
  onDone?: () => void
  onError?: (error: Event) => void
}

export type ChatSseHandle = {
  /** 主动断开连接（终止 fetch 流） */
  close: () => void
}

/**
 * 对话生成走 fetch + POST：EventSource 是原生 GET、不支持请求体，
 * 长提示词拼进 URL 会被 nginx（414）/ Tomcat（header 超限）直接拒绝。
 * 这里手动解析 SSE 帧（event:/data: 行），事件语义与后端保持一致：
 * 数据格式：data: {"d":"..."} ，结束事件：event: done
 * 推理内容：event: thinking, data: {"d":"..."}
 * 工具事件：event: tool_request / tool_executed, data: {"d":{"path":...}}
 */
export type BuildStatus = 'building' | 'success' | 'failed'

export type BuildEventPayload = {
  appId?: number
  status?: BuildStatus
  message?: string
  timestamp?: string
}

/** SSE 业务错误事件载荷（后端 GlobalExceptionHandler.handleSseError 构造） */
export type SseBusinessErrorPayload = {
  error?: boolean
  code?: number
  message?: string
}

export type BuildSseHandlers = {
  onBuilding?: (payload: BuildEventPayload) => void
  onSuccess?: (payload: BuildEventPayload) => void
  onFailed?: (payload: BuildEventPayload) => void
  /** 业务错误：订阅时刻即被拒（如无权限），流未开始 */
  onBusinessError?: (payload: SseBusinessErrorPayload) => void
  onError?: (error: Event) => void
}

/**
 * 订阅应用构建状态流（VUE 工程异步构建）
 * 数据格式：event: building / success / failed, data: {"appId":...,"status":...,"message":...}
 * 终态（success / failed）后端会自动结束流；收到终态后前端应主动 close，避免 EventSource 自动重连。
 */
export function connectBuildSse(
  appId: number | string,
  handlers: BuildSseHandlers,
): EventSource {
  const url = `/api/app/${appId}/build/stream`
  const eventSource = new EventSource(url, { withCredentials: true })

  const onNamedEvent = (name: BuildStatus) => (event: Event) => {
    const raw = (event as MessageEvent).data
    if (!raw) {
      return
    }
    let payload: BuildEventPayload
    try {
      payload = JSON.parse(raw) as BuildEventPayload
    } catch {
      return
    }
    if (name === 'building') {
      handlers.onBuilding?.(payload)
    } else if (name === 'success') {
      handlers.onSuccess?.(payload)
    } else {
      handlers.onFailed?.(payload)
    }
  }

  eventSource.addEventListener('building', onNamedEvent('building'))
  eventSource.addEventListener('success', onNamedEvent('success'))
  eventSource.addEventListener('failed', onNamedEvent('failed'))
  eventSource.addEventListener('business-error', (event: Event) => {
    const raw = (event as MessageEvent).data
    if (!raw) {
      return
    }
    try {
      handlers.onBusinessError?.(JSON.parse(raw) as SseBusinessErrorPayload)
    } catch {
      // 非 JSON 时忽略，交由后续的连接关闭错误统一处理
    }
  })

  // 不主动 close：网络抖动时让 EventSource 自动重连（后端 replay latest 会在重连后立即补发当前状态）
  eventSource.onerror = (error) => {
    handlers.onError?.(error)
  }

  return eventSource
}

/** 解析单个 SSE 帧（event:/data: 行）为事件名与原始 data */
const parseSseFrame = (frame: string): { event: string; data: string } | null => {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
    // id: / retry: / 注释行（: keep-alive）不影响本场景，忽略
  }
  if (!dataLines.length) {
    return null
  }
  return { event, data: dataLines.join('\n') }
}

export function connectChatSse(
  appId: number | string,
  message: string,
  handlers: SseHandlers,
): ChatSseHandle {
  const controller = new AbortController()
  let finished = false

  const parseData = (raw: string): unknown => {
    try {
      const parsed = JSON.parse(raw) as { d?: unknown }
      return parsed.d ?? null
    } catch {
      // 非 JSON 时按纯文本处理
      return raw
    }
  }

  const dispatch = (eventName: string, raw: string) => {
    if (eventName === 'done') {
      finished = true
      controller.abort()
      handlers.onDone?.()
      return
    }
    const data = parseData(raw)
    if (eventName === 'thinking') {
      if (typeof data === 'string') {
        handlers.onThinking?.(data)
      }
    } else if (eventName === 'tool_request') {
      if (data && typeof data === 'object') {
        handlers.onToolRequest?.(data as ToolRequestPayload)
      }
    } else if (eventName === 'tool_executed') {
      if (data && typeof data === 'object') {
        handlers.onToolExecuted?.(data as ToolExecutedPayload)
      }
    } else if (eventName === 'business-error') {
      try {
        handlers.onBusinessError?.(JSON.parse(raw) as SseBusinessErrorPayload)
      } catch {
        // 非 JSON 时忽略，交由后续的连接关闭错误统一处理
      }
    } else {
      // 无 event 行 / message 事件 = 正文分片
      if (typeof data === 'string') {
        handlers.onMessage(data)
      }
    }
  }

  const run = async () => {
    try {
      const response = await fetch('/api/app/chat/gen/code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        // appId 保持字符串：雪花 ID 超过 Number.MAX_SAFE_INTEGER，转数字会丢精度导致查不到应用
        body: JSON.stringify({ appId, message }),
        signal: controller.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(`SSE 连接失败：${response.status}`)
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      for (;;) {
        const { value, done } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        // SSE 帧以空行分隔，按 \n\n 切帧；剩余半帧留在 buffer 等下个 chunk
        let index: number
        while ((index = buffer.indexOf('\n\n')) !== -1) {
          const frame = buffer.slice(0, index)
          buffer = buffer.slice(index + 2)
          const parsed = parseSseFrame(frame)
          if (parsed) {
            dispatch(parsed.event, parsed.data)
          }
        }
      }
      // 连接自然结束：收到过 done 是正常终态；否则视为中途断开
      if (!finished && !controller.signal.aborted) {
        handlers.onError?.(new Event('error'))
      }
    } catch {
      // 主动 close（done / 用户停止）不算错误
      if (controller.signal.aborted || finished) {
        return
      }
      handlers.onError?.(new Event('error'))
    }
  }

  void run()

  return {
    close: () => {
      finished = true
      controller.abort()
    },
  }
}
