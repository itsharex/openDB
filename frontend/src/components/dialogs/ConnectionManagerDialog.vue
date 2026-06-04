<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog large">
      <div class="dialog-header">
        <div>
          <h2>连接管理</h2>
          <p class="subtitle">连接信息保存在本地文件 backend/data/connection-profiles.json</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-secondary btn-sm" :disabled="testingAll || profiles.length === 0" @click="testAll">
            {{ testingAll ? '测速中...' : '批量测速' }}
          </button>
          <button class="close-btn" @click="close">×</button>
        </div>
      </div>

      <div v-if="healthSummary && profiles.length" class="health-bar">
        <div class="health-item"><span>连接数</span><b>{{ healthSummary.total }}</b></div>
        <div class="health-item"><span>可达</span><b>{{ healthSummary.online }}/{{ healthSummary.total }}</b></div>
        <div class="health-item"><span>平均延迟</span><b>{{ formatLatency(healthSummary.avgLatencyMs || null) }}</b></div>
        <div v-if="healthSummary.fastest" class="health-item"><span>最快</span><b>{{ formatLatency(healthSummary.fastest.latencyMs) }}</b></div>
      </div>

      <div class="dialog-body scrollbar">
        <div v-if="loading" class="empty">加载中...</div>
        <div v-else-if="profiles.length === 0" class="empty">
          <p>暂无保存的连接</p>
          <button class="btn btn-primary btn-sm" @click="createNew">新建连接</button>
        </div>
        <div v-else class="profile-list">
          <div
            v-for="profile in profiles"
            :key="profile.id"
            class="profile-card"
            :class="{ recent: profile.id === lastProfileId }"
          >
            <div class="profile-main">
              <div class="profile-name">
                {{ profile.name }}
                <span class="type-badge">{{ profileTypeLabel(profile.type) }}</span>
                <span v-if="profile.id === lastProfileId" class="recent-badge">上次使用</span>
              </div>
              <div class="profile-meta">
                <span>{{ profile.host }}:{{ profile.port }}</span>
                <span>{{ profile.username }}</span>
                <span v-if="profile.database">库: {{ profile.database }}</span>
              </div>
              <div class="latency-row">
                <span class="latency-dot" :class="latencyClass(getLatency(profile.id)?.latencyMs ?? null)" />
                <span>{{ latencyStatus(profile.id) }}</span>
                <button
                  class="btn btn-ghost btn-xs"
                  :disabled="testingId === profile.id"
                  @click="testOne(profile)"
                >
                  {{ testingId === profile.id ? '测试中...' : '测速' }}
                </button>
              </div>
            </div>
            <div class="profile-actions">
              <button class="btn btn-primary btn-sm" @click="connect(profile.id)">连接</button>
              <button class="btn btn-secondary btn-sm" @click="edit(profile)">编辑</button>
              <button class="btn btn-ghost btn-sm danger" @click="remove(profile)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <button class="btn btn-secondary" @click="createNew">新建连接</button>
        <div class="spacer" />
        <button class="btn btn-secondary" @click="close">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { ConnectionProfile, ConnectionRequest } from '@/types'
import {
  formatLatency,
  getCachedLatency,
  latencyClass,
  latencyLabel,
  profileTypeLabel,
  saveLatencyResult,
  summarizeConnectionHealth,
  type ConnectionLatencyResult
} from '@/utils/connectionAnalytics'

const props = defineProps<{
  modelValue: boolean
  lastProfileId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  connect: [profileId: string]
  edit: [profile: ConnectionProfile]
  create: []
  changed: []
}>()

const profiles = ref<ConnectionProfile[]>([])
const loading = ref(false)
const testingAll = ref(false)
const testingId = ref('')
const latencyResults = ref<ConnectionLatencyResult[]>([])

watch(() => props.modelValue, visible => {
  if (visible) void load()
})

const healthSummary = computed(() =>
  profiles.value.length ? summarizeConnectionHealth(profiles.value, latencyResults.value) : null
)

async function load() {
  loading.value = true
  try {
    profiles.value = await api.listProfiles()
    latencyResults.value = profiles.value.map(p => {
      const cached = getCachedLatency(p.id)
      return {
        profileId: p.id,
        success: cached?.success ?? false,
        latencyMs: cached?.latencyMs ?? null
      }
    })
  } catch {
    profiles.value = []
  } finally {
    loading.value = false
  }
}

function getLatency(profileId: string) {
  return latencyResults.value.find(r => r.profileId === profileId)
}

function latencyStatus(profileId: string) {
  const item = getLatency(profileId)
  if (!item?.success || item.latencyMs == null) return '未测试'
  return `${formatLatency(item.latencyMs)} · ${latencyLabel(item.latencyMs)}`
}

function profileToRequest(profile: ConnectionProfile): ConnectionRequest {
  return {
    type: profile.type,
    name: profile.name,
    host: profile.host,
    port: profile.port,
    username: profile.username,
    database: profile.database
  }
}

async function testOne(profile: ConnectionProfile) {
  testingId.value = profile.id
  const start = performance.now()
  try {
    await api.testConnection(profileToRequest(profile))
    const latencyMs = performance.now() - start
    saveLatencyResult(profile.id, latencyMs, true)
    upsertResult({ profileId: profile.id, success: true, latencyMs })
  } catch (e) {
    saveLatencyResult(profile.id, 0, false)
    upsertResult({
      profileId: profile.id,
      success: false,
      latencyMs: null,
      error: e instanceof Error ? e.message : '连接失败'
    })
  } finally {
    testingId.value = ''
  }
}

async function testAll() {
  testingAll.value = true
  for (const profile of profiles.value) {
    await testOne(profile)
  }
  testingAll.value = false
}

function upsertResult(result: ConnectionLatencyResult) {
  const index = latencyResults.value.findIndex(r => r.profileId === result.profileId)
  if (index >= 0) latencyResults.value[index] = result
  else latencyResults.value.push(result)
}

function close() {
  emit('update:modelValue', false)
}

function connect(profileId: string) {
  emit('connect', profileId)
  close()
}

function edit(profile: ConnectionProfile) {
  emit('edit', profile)
}

async function remove(profile: ConnectionProfile) {
  if (!window.confirm(`确认删除保存的连接「${profile.name}」？`)) return
  await api.deleteProfile(profile.id)
  await load()
  emit('changed')
}

function createNew() {
  emit('create')
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.large { width: 680px; max-height: calc(100vh - 48px); display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; gap: 12px; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.dialog-header h2 { margin: 0; font-size: 14px; }
.subtitle { margin: 4px 0 0; font-size: 11px; color: var(--text-muted); }
.header-actions { display: flex; align-items: center; gap: 8px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.health-bar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding: 10px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.health-item { text-align: center; font-size: 11px; }
.health-item span { display: block; color: var(--text-muted); margin-bottom: 2px; }
.health-item b { color: var(--accent); font-size: 14px; }
.dialog-body { padding: 16px; overflow: auto; flex: 1; }
.empty { padding: 32px 16px; text-align: center; color: var(--text-muted); font-size: 13px; }
.profile-list { display: flex; flex-direction: column; gap: 10px; }
.profile-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-secondary); }
.profile-card.recent { border-color: #b3ccff; background: var(--accent-light); }
.profile-name { font-size: 13px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.type-badge { font-size: 10px; font-weight: 500; color: var(--text-secondary); background: var(--bg-tertiary); padding: 1px 6px; border-radius: 999px; }
.recent-badge { font-size: 10px; font-weight: 500; color: var(--accent); background: var(--bg-primary); border: 1px solid #b3ccff; border-radius: 999px; padding: 1px 6px; }
.profile-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; font-size: 11px; color: var(--text-muted); }
.latency-row { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 11px; color: var(--text-secondary); }
.latency-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--text-muted); flex-shrink: 0; }
.latency-dot.good { background: var(--success); }
.latency-dot.ok { background: var(--accent); }
.latency-dot.slow { background: var(--warning); }
.latency-dot.bad { background: var(--danger); }
.profile-actions { display: flex; gap: 6px; flex-shrink: 0; }
.danger { color: var(--danger); }
.dialog-footer { display: flex; align-items: center; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.spacer { flex: 1; }
.btn-sm { padding: 4px 10px; font-size: 11px; }
.btn-xs { padding: 2px 6px; font-size: 10px; }
</style>
