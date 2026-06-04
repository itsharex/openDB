<template>
  <div class="ai-panel">
    <div class="status-bar">
      <div class="status-text">
        <span class="status-dot" :class="{ online: aiOnline, working: loading }" />
        <span v-if="loading && aiStatusText" class="ai-working-status">{{ aiStatusText }}</span>
        <span v-else-if="aiOnline">{{ aiStatus?.providerLabel }} · {{ aiStatus?.model }}</span>
        <span v-else-if="aiStatus?.enabled">已启用，请完成 AI 配置</span>
        <span v-else>AI 未启用</span>
      </div>
      <button type="button" class="btn btn-ghost btn-sm" @click="$emit('open-settings')">AI 设置</button>
    </div>

    <div class="quick-actions">
      <button
        v-for="action in quickActions"
        :key="action"
        class="chip"
        :disabled="!connectionId || loading"
        @click="runQuickAction(action)"
      >
        {{ action }}
      </button>
    </div>

    <div ref="messagesRef" class="messages scrollbar">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="message-row"
        :class="message.role"
      >
        <div class="avatar" :class="message.role">
          {{ message.role === 'user' ? '你' : 'AI' }}
        </div>
        <div class="bubble" :class="{ streaming: streamingIndex === index }">
          <div
            v-if="message.role === 'assistant' && streamingIndex === index && aiStatusText && !message.content"
            class="phase-status"
          >
            <span class="phase-dot" />
            <span>{{ aiStatusText }}</span>
          </div>
          <div v-if="message.content" class="content markdown-body" v-html="renderMarkdown(message.content)"></div>
          <div
            v-if="message.role === 'assistant' && streamingIndex === index && message.content"
            class="streaming-indicator inline"
          >▍</div>
          <button
            v-if="message.role === 'assistant' && streamingIndex !== index && extractSql(message.content)"
            class="apply-btn"
            @click="$emit('apply-sql', extractSql(message.content)!)"
          >
            应用到编辑器
          </button>
        </div>
      </div>
    </div>

    <form class="composer" @submit.prevent="submitForm">
      <textarea
        v-model="prompt"
        rows="3"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="!connectionId || loading"
        @keydown="onPromptKeyDown"
      />
      <button class="btn btn-primary" type="submit" :disabled="!connectionId || !prompt.trim() || loading">
        {{ loading ? (aiStatusText || '处理中...') : '发送' }}
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { marked } from 'marked'
import { api } from '@/api'
import { extractAiSql } from '@/utils/sqlFormatter'
import type { AiStatus } from '@/types'

const props = defineProps<{
  connectionId: string
  database: string
  sqlText: string
  aiStatus: AiStatus | null
}>()

const emit = defineEmits<{
  'apply-sql': [sql: string]
  'open-settings': []
  'status-change': [status: string]
}>()

const aiOnline = computed(() => !!(props.aiStatus?.enabled && props.aiStatus?.configured))

const quickActions = [
  '解释当前 SQL',
  '优化当前 SQL',
  '生成建表语句',
  '常用查询模板'
]

const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([
  {
    role: 'assistant',
    content: '你好，我是 openDB AI 助手。你可以让我生成 SQL、解释查询、优化语句，或基于当前数据库结构给出建议。若尚未配置，请点击右上角「AI 设置」。'
  }
])
const prompt = ref('')
const loading = ref(false)
const aiStatusText = ref('')
const streamingIndex = ref<number | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
let streamAbort: AbortController | null = null
let scrollScheduled = false

function setAiStatus(status: string) {
  if (!status || status === '完成') {
    aiStatusText.value = ''
    emit('status-change', '')
    return
  }
  aiStatusText.value = status
  emit('status-change', status)
}

onBeforeUnmount(() => {
  streamAbort?.abort()
})

watch(
  () => props.aiStatus,
  status => {
    if (!status) return
    if (!status.enabled) {
      messages.value = [{
        role: 'assistant',
        content: 'AI 助手尚未启用。请点击右上角 **AI 设置**，选择 OpenAI、Claude、DeepSeek、Ollama 等提供商并完成配置。'
      }]
    }
  },
  { immediate: true }
)

watch(
  () => props.connectionId,
  () => {
    messages.value = [{
      role: 'assistant',
      content: '连接已切换。我可以结合当前数据库结构来回答你的问题。'
    }]
  }
)

function renderMarkdown(content: string) {
  return marked.parse(content, { breaks: true }) as string
}

function extractSql(content: string) {
  return extractAiSql(content)
}

async function scrollToBottom() {
  if (scrollScheduled) return
  scrollScheduled = true
  await nextTick()
  requestAnimationFrame(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
    scrollScheduled = false
  })
}

async function send(customPrompt?: string, autoApplySql = false) {
  const text = (customPrompt ?? prompt.value).trim()
  if (!text || !props.connectionId || loading.value) return

  streamAbort?.abort()
  streamAbort = new AbortController()

  messages.value.push({ role: 'user', content: text })
  prompt.value = ''
  loading.value = true
  setAiStatus('正在连接...')

  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  streamingIndex.value = assistantIndex
  await scrollToBottom()

  try {
    await api.aiChatStream(
      props.connectionId,
      {
        prompt: text,
        database: props.database || undefined,
        contextSql: props.sqlText || undefined
      },
      {
        onStatus: setAiStatus,
        onDelta: chunk => {
          messages.value[assistantIndex].content += chunk
          void scrollToBottom()
        }
      },
      streamAbort.signal
    )
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return
    }
    const message = error instanceof Error ? error.message : 'AI request failed'
    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = message
    } else {
      messages.value[assistantIndex].content += `\n\n**错误:** ${message}`
    }
  } finally {
    streamingIndex.value = null
    loading.value = false
    setAiStatus('')
    streamAbort = null
    if (autoApplySql) {
      const sql = extractAiSql(messages.value[assistantIndex].content)
      if (sql) emit('apply-sql', sql)
    }
    await scrollToBottom()
  }
}

async function submitForm() {
  await send()
}

function onPromptKeyDown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void submitForm()
}

function runQuickAction(action: string) {
  if (action.includes('当前 SQL') && props.sqlText.trim()) {
    send(`${action}:\n${props.sqlText}`)
    return
  }
  send(action)
}

function sendPrompt(text: string, options?: { autoApplySql?: boolean }) {
  return send(text, options?.autoApplySql)
}

defineExpose({ sendPrompt })
</script>

<style scoped>
.ai-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 10px;
}

.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-primary);
}

.status-text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-secondary);
  min-width: 0;
}

.status-text span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  flex-shrink: 0;
}

.status-dot.online {
  background: var(--success);
}

.status-dot.working {
  background: var(--accent);
  animation: pulse 1.2s ease-in-out infinite;
}

.ai-working-status {
  color: var(--accent);
  font-weight: 600;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.85); }
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.chip {
  border: 1px solid var(--border);
  background: var(--bg-primary);
  color: var(--text-secondary);
  border-radius: var(--radius);
  padding: 4px 8px;
  font-size: 11px;
}

.chip:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}

.chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 10px;
  padding: 8px 4px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.message-row.user {
  flex-direction: row-reverse;
  justify-content: flex-start;
}

.message-row.assistant {
  justify-content: flex-start;
}

.avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  margin-top: 2px;
}

.avatar.user {
  background: var(--accent);
  color: #fff;
}

.avatar.assistant {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  border: 1px solid var(--border);
}

.bubble {
  max-width: min(88%, 360px);
  min-width: 0;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg-primary);
  box-shadow: var(--shadow-sm);
}

.message-row.user .bubble {
  background: var(--accent-light);
  border-color: #b3ccff;
  border-top-right-radius: 4px;
}

.message-row.assistant .bubble {
  background: var(--bg-primary);
  border-top-left-radius: 4px;
}

.message-row.assistant .bubble.streaming {
  border-color: #b3ccff;
  box-shadow: 0 0 0 1px rgba(51, 112, 255, 0.08);
}

.phase-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--accent-light);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
}

.phase-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.2s ease-in-out infinite;
}

.content {
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.message-row.user .content :deep(p:last-child),
.message-row.assistant .content :deep(p:last-child) {
  margin-bottom: 0;
}

.streaming-indicator {
  display: inline-block;
  margin-top: 4px;
  color: var(--accent);
  font-size: 14px;
  animation: blink 1s step-end infinite;
}

.streaming-indicator.inline {
  margin-top: 0;
  margin-left: 2px;
  vertical-align: baseline;
}

@keyframes blink {
  50% { opacity: 0; }
}

.apply-btn {
  margin-top: 8px;
  background: var(--accent-light);
  border: 1px solid #b3ccff;
  color: var(--accent);
  border-radius: var(--radius);
  padding: 4px 8px;
  font-size: 11px;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer textarea {
  resize: none;
  font-size: 12px;
}
</style>
