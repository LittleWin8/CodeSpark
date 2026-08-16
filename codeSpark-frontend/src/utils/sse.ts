export type ToolRequestPayload = {
  path?: string
}

export type ToolExecutedPayload = {
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
  onDone?: () => void
  onError?: (error: Event) => void
}

/**
 * 通过 EventSource 连接后端 SSE 对话接口
 * 数据格式：data: {"d":"..."} ，结束事件：event: done
 * 推理内容：event: thinking, data: {"d":"..."}
 * 工具事件：event: tool_request / tool_executed, data: {"d":{"path":...}}
 */
export function connectChatSse(
  appId: number | string,
  message: string,
  handlers: SseHandlers,
): EventSource {
  const params = new URLSearchParams({
    appId: String(appId),
    message,
  })
  const url = `/api/app/chat/gen/code?${params.toString()}`
  const eventSource = new EventSource(url, { withCredentials: true })

  const parseData = (raw: string): unknown => {
    try {
      const parsed = JSON.parse(raw) as { d?: unknown }
      return parsed.d ?? null
    } catch {
      // 非 JSON 时按纯文本处理
      return raw
    }
  }

  eventSource.onmessage = (event) => {
    const raw = event.data
    if (!raw) {
      return
    }
    const data = parseData(raw)
    if (typeof data === 'string') {
      handlers.onMessage(data)
    }
  }

  eventSource.addEventListener('thinking', (event) => {
    const raw = (event as MessageEvent).data
    if (!raw) {
      return
    }
    const data = parseData(raw)
    if (typeof data === 'string') {
      handlers.onThinking?.(data)
    }
  })

  eventSource.addEventListener('tool_request', (event) => {
    const raw = (event as MessageEvent).data
    if (!raw) {
      return
    }
    const data = parseData(raw)
    if (data && typeof data === 'object') {
      handlers.onToolRequest?.(data as ToolRequestPayload)
    }
  })

  eventSource.addEventListener('tool_executed', (event) => {
    const raw = (event as MessageEvent).data
    if (!raw) {
      return
    }
    const data = parseData(raw)
    if (data && typeof data === 'object') {
      handlers.onToolExecuted?.(data as ToolExecutedPayload)
    }
  })

  eventSource.addEventListener('done', () => {
    eventSource.close()
    handlers.onDone?.()
  })

  eventSource.onerror = (error) => {
    eventSource.close()
    handlers.onError?.(error)
  }

  return eventSource
}
