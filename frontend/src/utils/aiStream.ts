export interface AiStreamHandlers {
  onDelta: (chunk: string) => void
  onStatus?: (status: string) => void
  onDone?: () => void
  onError?: (message: string) => void
}

function parseSseBlock(block: string, handlers: AiStreamHandlers) {
  if (!block.trim()) return

  let event = 'message'
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  const data = dataLines.join('\n')
  if (event === 'delta') {
    handlers.onDelta(data)
  } else if (event === 'status') {
    handlers.onStatus?.(data)
  } else if (event === 'done') {
    handlers.onDone?.()
  } else if (event === 'error') {
    throw new Error(data || 'AI 流式请求失败')
  }
}

export async function readAiSseStream(response: Response, handlers: AiStreamHandlers) {
  if (!response.ok) {
    let message = `请求失败 (${response.status})`
    try {
      const payload = await response.json()
      if (payload?.message) message = payload.message
    } catch {
      // ignore
    }
    throw new Error(message)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式响应')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      parseSseBlock(block, handlers)
    }
  }

  if (buffer.trim()) {
    parseSseBlock(buffer, handlers)
  }
}
