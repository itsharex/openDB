<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog large">
      <div class="dialog-header"><h2>结构同步</h2><button class="close-btn" @click="close">×</button></div>
      <div class="dialog-body">
        <div class="grid-2">
          <label>源数据库<input v-model="sourceDatabase" /></label>
          <label>目标数据库<input v-model="targetDatabase" /></label>
        </div>
        <button class="btn btn-primary btn-sm" :disabled="loading" @click="compare">{{ loading ? '对比中...' : '对比结构' }}</button>
        <div v-if="result" class="compare-result">
          <div class="summary-cards">
            <div class="card source-only">
              <span>仅在源库</span>
              <strong>{{ result.onlyInSource.length }}</strong>
            </div>
            <div class="card target-only">
              <span>仅在目标库</span>
              <strong>{{ result.onlyInTarget.length }}</strong>
            </div>
            <div class="card modified">
              <span>结构差异</span>
              <strong>{{ result.modifiedTables.length }}</strong>
            </div>
          </div>

          <div v-if="result.onlyInSource.length" class="list-block">
            <h4>仅在源库 ({{ result.onlyInSource.length }})</h4>
            <div class="tag-list">
              <span v-for="name in result.onlyInSource" :key="name" class="tag add">{{ name }}</span>
            </div>
          </div>
          <div v-if="result.onlyInTarget.length" class="list-block">
            <h4>仅在目标库 ({{ result.onlyInTarget.length }})</h4>
            <div class="tag-list">
              <span v-for="name in result.onlyInTarget" :key="name" class="tag drop">{{ name }}</span>
            </div>
          </div>
          <div v-if="result.modifiedTables.length" class="list-block">
            <h4>结构差异 ({{ result.modifiedTables.length }})</h4>
            <div class="tag-list">
              <span v-for="name in result.modifiedTables" :key="name" class="tag mod">{{ name }}</span>
            </div>
          </div>

          <div v-if="syncInsights.length" class="insights">
            <div v-for="(item, i) in syncInsights" :key="i" class="insight" :class="item.level">
              <strong>{{ item.title }}</strong> — {{ item.detail }}
            </div>
          </div>

          <label>同步脚本<textarea :value="result.syncScript" rows="10" readonly spellcheck="false" /></label>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" :disabled="!result" @click="copyScript">复制脚本</button>
        <button class="btn btn-primary" @click="close">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { SchemaCompareResult } from '@/types/features'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const sourceDatabase = ref('')
const targetDatabase = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<SchemaCompareResult | null>(null)

watch(() => props.modelValue, v => {
  if (v) {
    sourceDatabase.value = props.database
    targetDatabase.value = ''
    result.value = null
    error.value = ''
  }
})

const syncInsights = computed(() => {
  if (!result.value) return []
  const items: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }> = []
  const totalChanges = result.value.onlyInSource.length + result.value.onlyInTarget.length + result.value.modifiedTables.length
  if (totalChanges === 0) {
    items.push({ level: 'good', title: '结构一致', detail: '源库与目标库表结构完全匹配。' })
  } else {
    items.push({
      level: totalChanges > 5 ? 'warn' : 'info',
      title: '待同步变更',
      detail: `共 ${totalChanges} 项差异，执行前请备份目标库并在测试环境验证脚本。`
    })
  }
  if (result.value.onlyInTarget.length > 0) {
    items.push({
      level: 'warn',
      title: '目标库多余表',
      detail: `${result.value.onlyInTarget.length} 张表仅存在于目标库，同步脚本可能包含 DROP 操作。`
    })
  }
  return items
})

function close() { emit('update:modelValue', false) }

async function compare() {
  loading.value = true
  error.value = ''
  try {
    result.value = await api.compareSchemas(props.connectionId, sourceDatabase.value, targetDatabase.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '对比失败'
  } finally {
    loading.value = false
  }
}

function copyScript() {
  if (result.value) navigator.clipboard.writeText(result.value.syncScript)
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.large { width: 680px; max-height: 85vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 10px; overflow: auto; flex: 1; min-height: 0; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.summary-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
.card { border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; text-align: center; background: var(--bg-secondary); }
.card span { display: block; font-size: 11px; color: var(--text-muted); }
.card strong { font-size: 22px; color: var(--accent); }
.list-block h4 { margin: 0 0 6px; font-size: 11px; color: var(--text-secondary); }
.tag-list { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.tag { font-size: 10px; padding: 2px 8px; border-radius: 999px; }
.tag.add { background: #e6f4ea; color: #1a7f37; }
.tag.drop { background: var(--danger-bg); color: var(--danger); }
.tag.mod { background: var(--accent-light); color: var(--accent); }
.insights { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
.insight { font-size: 11px; padding: 6px 8px; border-radius: var(--radius); background: var(--bg-secondary); }
.insight.good { border-left: 3px solid var(--success); }
.insight.warn { border-left: 3px solid var(--warning); }
.insight.info { border-left: 3px solid var(--accent); }
textarea { font-family: monospace; font-size: 11px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border); }
.error { color: var(--danger); font-size: 12px; }
.btn-sm { padding: 4px 10px; font-size: 11px; align-self: flex-start; }
</style>
