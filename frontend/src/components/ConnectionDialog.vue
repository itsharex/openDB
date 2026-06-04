<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog">
      <div class="dialog-header">
        <div>
          <h2>{{ editingProfile ? '编辑连接' : `新建 ${selectedTypeLabel} 连接` }}</h2>
          <p class="subtitle">连接信息将自动保存到本地 backend/data/connection-profiles.json</p>
        </div>
        <button class="close-btn" @click="close">×</button>
      </div>

      <form class="dialog-body" @submit.prevent="submit">
        <label>
          连接名称
          <input v-model="form.name" placeholder="Local MySQL" required />
        </label>

        <label>
          数据库类型
          <select v-model="form.type">
            <option v-for="item in databaseTypes" :key="item.value" :value="item.value" :disabled="!item.supported">
              {{ item.label }}{{ item.supported ? '' : ' (Coming soon)' }}
            </option>
          </select>
        </label>

        <div class="grid-2">
          <label>
            主机
            <input v-model="form.host" placeholder="127.0.0.1" required />
          </label>
          <label>
            端口
            <input v-model.number="form.port" type="number" min="1" max="65535" required />
          </label>
        </div>

        <label>
          用户名
          <input v-model="form.username" placeholder="root" required />
        </label>

        <label>
          密码
          <input v-model="form.password" type="password" :placeholder="editingProfile ? '留空则保持已保存密码' : '可选'" />
        </label>

        <label>
          默认数据库 / 服务名
          <input v-model="form.database" :placeholder="databaseFieldHint" />
        </label>

        <p v-if="success" class="success">{{ success }}</p>
        <p v-if="latencyMs != null && testOk" class="latency-result" :class="latencyClass(latencyMs)">
          连接延迟 {{ formatLatency(latencyMs) }} · {{ latencyLabel(latencyMs) }}
        </p>
        <p v-if="error" class="error">{{ error }}</p>

        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" :disabled="testing" @click="testConnection">
            {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            {{ submitting ? '处理中...' : editingProfile ? '保存' : '保存并连接' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '@/api'
import type { ConnectionInfo, ConnectionProfile, ConnectionRequest, DatabaseTypeInfo } from '@/types'
import { getDatabaseFieldHint, getDatabaseTypeLabel, getDefaultPort } from '@/utils/sqlDialect'
import { formatLatency, latencyClass, latencyLabel } from '@/utils/connectionAnalytics'

const props = defineProps<{
  modelValue: boolean
  databaseTypes: DatabaseTypeInfo[]
  editingProfile?: ConnectionProfile | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [connection: ConnectionInfo, form: ConnectionRequest, profileId?: string]
  updated: [profile: ConnectionProfile]
  notify: [payload: { type: 'success' | 'error'; message: string }]
}>()

const form = reactive<ConnectionRequest>({
  type: 'MYSQL',
  name: 'Local MySQL',
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  password: '',
  database: ''
})

const testing = ref(false)
const submitting = ref(false)
const error = ref('')
const success = ref('')
const latencyMs = ref<number | null>(null)
const testOk = ref(false)

const editingProfile = computed(() => props.editingProfile ?? null)
const selectedTypeLabel = computed(() => getDatabaseTypeLabel(form.type))
const databaseFieldHint = computed(() => getDatabaseFieldHint(form.type))

watch(
  () => form.type,
  type => {
    form.port = getDefaultPort(type)
  }
)

watch(
  () => [props.modelValue, props.editingProfile] as const,
  ([visible, profile]) => {
    if (!visible) return
    error.value = ''
    success.value = ''
    latencyMs.value = null
    testOk.value = false
    if (profile) {
      form.type = profile.type
      form.name = profile.name
      form.host = profile.host
      form.port = profile.port
      form.username = profile.username
      form.password = ''
      form.database = profile.database || ''
      return
    }
    Object.assign(form, {
      type: 'MYSQL',
      name: 'Local MySQL',
      host: '127.0.0.1',
      port: 3306,
      username: 'root',
      password: '',
      database: ''
    })
  }
)

function close() {
  emit('update:modelValue', false)
}

function buildPayload(): ConnectionRequest {
  const payload: ConnectionRequest = {
    type: form.type,
    name: form.name.trim(),
    host: form.host.trim(),
    port: form.port,
    username: form.username.trim(),
    database: form.database?.trim() || undefined
  }
  if (form.password?.trim()) payload.password = form.password.trim()
  return payload
}

async function testConnection() {
  testing.value = true
  error.value = ''
  success.value = ''
  latencyMs.value = null
  testOk.value = false
  const start = performance.now()
  try {
    await api.testConnection(buildPayload())
    latencyMs.value = performance.now() - start
    testOk.value = true
    success.value = `连接测试成功！延迟 ${formatLatency(latencyMs.value)}（${latencyLabel(latencyMs.value)}）`
    emit('notify', { type: 'success', message: success.value })
  } catch (err) {
    const message = err instanceof Error ? err.message : '测试连接失败'
    error.value = message
    emit('notify', { type: 'error', message })
  } finally {
    testing.value = false
  }
}

async function submit() {
  submitting.value = true
  error.value = ''
  success.value = ''
  try {
    const payload = buildPayload()
    if (editingProfile.value) {
      const profile = await api.updateProfile(editingProfile.value.id, payload)
      emit('updated', profile)
      emit('notify', { type: 'success', message: '连接已保存' })
      close()
      return
    }
    const connection = await api.createConnection(payload)
    emit('created', connection, payload, connection.profileId)
    emit('notify', { type: 'success', message: `已连接 ${connection.name}` })
    close()
  } catch (err) {
    const message = err instanceof Error ? err.message : '操作失败'
    error.value = message
    emit('notify', { type: 'error', message })
  } finally {
    submitting.value = false
  }
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

.dialog {
  width: 520px;
  max-width: calc(100vw - 32px);
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-shadow: var(--shadow-lg);
}

.dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
  border-radius: 6px 6px 0 0;
}

.dialog-header h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.subtitle {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--text-muted);
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 24px;
  line-height: 1;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 12px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.success {
  margin: 0;
  color: #1a7f37;
  font-size: 13px;
  background: var(--success-bg);
  border: 1px solid #a8e6b4;
  border-radius: var(--radius);
  padding: 8px 10px;
}

.error {
  margin: 0;
  color: var(--danger);
  font-size: 13px;
  background: var(--danger-bg);
  border: 1px solid #ffc9c5;
  border-radius: var(--radius);
  padding: 8px 10px;
}

.latency-result {
  margin: 0;
  font-size: 12px;
  border-radius: var(--radius);
  padding: 8px 10px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
}

.latency-result.good { color: #1a7f37; border-color: #a8e6b4; background: var(--success-bg); }
.latency-result.ok { color: var(--accent); border-color: #b3ccff; background: var(--accent-light); }
.latency-result.slow { color: #b8860b; border-color: #ffe58f; }
.latency-result.bad { color: var(--danger); border-color: #ffc9c5; background: var(--danger-bg); }
</style>
