<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog large">
      <div class="dialog-header">
        <h2>AI 设置</h2>
        <button class="close-btn" @click="close">×</button>
      </div>

      <div class="dialog-body scrollbar">
        <section class="section">
          <label class="checkbox">
            <input v-model="form.enabled" type="checkbox" />
            启用 AI 助手
          </label>
          <p class="hint">启用后可使用 SQL 生成、解释、优化等功能。配置保存在本地 backend/data/ai-config.json。</p>
        </section>

        <section class="section">
          <h3>提供商</h3>
          <label>
            AI 提供商
            <select v-model="form.provider" @change="applyPreset">
              <option v-for="preset in presets" :key="preset.id" :value="preset.id">
                {{ preset.label }}
              </option>
            </select>
          </label>
          <p v-if="currentPreset?.description" class="hint">{{ currentPreset.description }}</p>
        </section>

        <section class="section">
          <h3>连接</h3>
          <label>
            API 地址
            <input v-model="form.apiUrl" placeholder="https://api.openai.com/v1/chat/completions" required />
          </label>
          <label>
            API Key
            <input
              v-model="form.apiKey"
              type="password"
              :placeholder="config?.hasApiKey ? `已保存 ${config.apiKeyMasked}，留空则不修改` : '输入 API Key'"
            />
          </label>
          <label>
            模型
            <input v-model="form.model" list="ai-model-suggestions" placeholder="gpt-4o-mini" required />
            <datalist id="ai-model-suggestions">
              <option v-for="model in suggestedModels" :key="model" :value="model" />
            </datalist>
          </label>
          <label v-if="form.provider === 'AZURE_OPENAI'">
            Azure API Version
            <input v-model="form.apiVersion" placeholder="2024-02-15-preview" />
          </label>
        </section>

        <section class="section">
          <h3>高级</h3>
          <div class="grid-2">
            <label>
              超时 (秒)
              <input v-model.number="form.timeoutSeconds" type="number" min="10" max="300" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="form.maxTokens" type="number" min="256" max="128000" step="256" />
            </label>
          </div>
          <label>
            温度 {{ form.temperature }}
            <input v-model.number="form.temperature" type="range" min="0" max="2" step="0.1" />
          </label>
        </section>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="testResult" class="success-text">{{ testResult }}</p>
      </div>

      <div class="dialog-footer">
        <button class="btn btn-secondary" :disabled="testing" @click="testConnection">
          {{ testing ? '测试中...' : '测试连接' }}
        </button>
        <div class="spacer" />
        <button class="btn btn-secondary" @click="close">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '@/api'
import type { AiConfig, AiConfigRequest, AiProviderPreset } from '@/types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [AiConfig] }>()

const presets = ref<AiProviderPreset[]>([])
const config = ref<AiConfig | null>(null)
const form = reactive<AiConfigRequest>({
  enabled: false,
  provider: 'OPENAI',
  apiUrl: '',
  apiKey: '',
  model: '',
  apiVersion: '2024-02-15-preview',
  timeoutSeconds: 60,
  temperature: 0.2,
  maxTokens: 4096
})

const saving = ref(false)
const testing = ref(false)
const error = ref('')
const testResult = ref('')

const currentPreset = computed(() => presets.value.find(item => item.id === form.provider))
const suggestedModels = computed(() => currentPreset.value?.suggestedModels ?? [])

watch(() => props.modelValue, open => {
  if (open) void load()
})

async function load() {
  error.value = ''
  testResult.value = ''
  form.apiKey = ''
  try {
    const [providerList, current] = await Promise.all([
      api.listAiProviders(),
      api.getAiConfig()
    ])
    presets.value = providerList
    config.value = current
    applyConfig(current)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载 AI 配置失败'
  }
}

function applyConfig(current: AiConfig) {
  form.enabled = current.enabled
  form.provider = current.provider
  form.apiUrl = current.apiUrl
  form.model = current.model
  form.apiVersion = current.apiVersion || '2024-02-15-preview'
  form.timeoutSeconds = current.timeoutSeconds
  form.temperature = current.temperature
  form.maxTokens = current.maxTokens
}

function applyPreset() {
  const preset = currentPreset.value
  if (!preset) return
  if (preset.defaultApiUrl) form.apiUrl = preset.defaultApiUrl
  if (preset.defaultModel) form.model = preset.defaultModel
}

function buildPayload(): AiConfigRequest {
  const payload: AiConfigRequest = {
    enabled: form.enabled,
    provider: form.provider,
    apiUrl: form.apiUrl.trim(),
    model: form.model.trim(),
    apiVersion: form.apiVersion?.trim(),
    timeoutSeconds: form.timeoutSeconds,
    temperature: form.temperature,
    maxTokens: form.maxTokens
  }
  if (form.apiKey?.trim()) payload.apiKey = form.apiKey.trim()
  return payload
}

async function save() {
  saving.value = true
  error.value = ''
  testResult.value = ''
  try {
    const saved = await api.updateAiConfig(buildPayload())
    config.value = saved
    form.apiKey = ''
    emit('saved', saved)
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  testing.value = true
  error.value = ''
  testResult.value = ''
  try {
    await api.updateAiConfig(buildPayload())
    const result = await api.testAiConfig()
    testResult.value = `连接成功，模型回复：${result.reply}`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '测试失败'
  } finally {
    testing.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
}

.dialog.large {
  width: 560px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-shadow: var(--shadow-lg);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.dialog-header h2 {
  margin: 0;
  font-size: 14px;
}

.close-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  color: var(--text-muted);
}

.dialog-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section h3 {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.hint {
  margin: 0;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
}

.error-text {
  margin: 0;
  font-size: 12px;
  color: var(--danger);
}

.success-text {
  margin: 0;
  font-size: 12px;
  color: #1a7f37;
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.spacer {
  flex: 1;
}
</style>
