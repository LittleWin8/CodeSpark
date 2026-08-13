export type SseHandlers = {
  onMessage: (chunk: string) => void
  onDone?: () => void
  onError?: (error: Event) => void
}

/**
 * 通过 EventSource 连接后端 SSE 对话接口
 * 数据格式：data: {"d":"..."} ，结束事件：event: done
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

  eventSource.onmessage = (event) => {
    const raw = event.data
    if (!raw) {
      return
    }
    try {
      const parsed = JSON.parse(raw) as { d?: string }
      if (typeof parsed.d === 'string') {
        handlers.onMessage(parsed.d)
      }
    } catch {
      // 非 JSON 时按纯文本追加
      handlers.onMessage(raw)
    }
  }

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
