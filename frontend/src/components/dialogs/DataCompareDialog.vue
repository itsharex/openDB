<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header"><h2>数据对比</h2><button class="close-btn" @click="close">×</button></div>
      <form class="dialog-body" @submit.prevent="submit">
        <div class="grid-2">
          <label>源数据库<input v-model="sourceDatabase" required /></label>
          <label>源表<input v-model="sourceTable" required /></label>
          <label>目标数据库<input v-model="targetDatabase" required /></label>
          <label>目标表<input v-model="targetTable" required /></label>
        </div>
        <div v-if="result" class="result-panel">
          <div class="metric-grid">
            <div class="metric-card source">
              <span>源表行数</span>
              <strong>{{ result.sourceRows.toLocaleString() }}</strong>
              <small>{{ result.sourceDatabase }}.{{ result.sourceTable }}</small>
            </div>
            <div class="metric-card target">
              <span>目标表行数</span>
              <strong>{{ result.targetRows.toLocaleString() }}</strong>
              <small>{{ result.targetDatabase }}.{{ result.targetTable }}</small>
            </div>
            <div class="metric-card diff" :class="diffClass">
              <span>行数差异</span>
              <strong>{{ diffLabel }}</strong>
              <small>{{ diffPct }}</small>
            </div>
          </div>
          <div class="compare-bar">
            <div class="bar-segment source" :style="{ flex: result.sourceRows || 1 }">
              源 {{ result.sourceRows.toLocaleString() }}
            </div>
            <div class="bar-segment target" :style="{ flex: result.targetRows || 1 }">
              目标 {{ result.targetRows.toLocaleString() }}
            </div>
          </div>
          <div class="summary-box">{{ result.summary }}</div>
          <div v-if="insights.length" class="insights">
            <div v-for="(item, i) in insights" :key="i" class="insight" :class="item.level">
              <strong>{{ item.title }}</strong> — {{ item.detail }}
            </div>
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="dialog-footer">
          <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '对比中...' : '对比' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { DataCompareResult } from '@/types/features'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string; table?: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const sourceDatabase = ref('')
const sourceTable = ref('')
const targetDatabase = ref('')
const targetTable = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<DataCompareResult | null>(null)

watch(() => props.modelValue, v => {
  if (v) {
    sourceDatabase.value = props.database
    sourceTable.value = props.table ?? ''
    targetDatabase.value = props.database
    targetTable.value = props.table ?? ''
    result.value = null
    error.value = ''
  }
})

const rowDiff = computed(() => {
  if (!result.value) return 0
  return result.value.targetRows - result.value.sourceRows
})

const diffLabel = computed(() => {
  const d = rowDiff.value
  if (d === 0) return '一致'
  return d > 0 ? `+${d.toLocaleString()}` : d.toLocaleString()
})

const diffPct = computed(() => {
  if (!result.value || !result.value.sourceRows) return ''
  const pct = (rowDiff.value / result.value.sourceRows) * 100
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}% 相对源表`
})

const diffClass = computed(() => {
  if (!result.value) return ''
  if (result.value.sourceRows === result.value.targetRows) return 'match'
  return Math.abs(rowDiff.value) / Math.max(result.value.sourceRows, 1) > 0.1 ? 'warn' : 'info'
})

const insights = computed(() => {
  if (!result.value) return []
  const items: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }> = []
  if (result.value.sourceRows === result.value.targetRows) {
    items.push({ level: 'good', title: '行数一致', detail: '两表记录总数相同，可进行抽样或 checksum 进一步校验。' })
  } else {
    const ratio = result.value.targetRows / Math.max(result.value.sourceRows, 1)
    if (ratio < 0.9 || ratio > 1.1) {
      items.push({ level: 'warn', title: '行数偏差较大', detail: '差异超过 10%，建议检查同步/迁移是否完整。' })
    } else {
      items.push({ level: 'info', title: '行数接近', detail: '存在小幅差异，可能是增量写入或删除导致。' })
    }
  }
  if (result.value.sourceRows === 0 || result.value.targetRows === 0) {
    items.push({ level: 'warn', title: '空表检测', detail: '其中一侧为空表，请确认对比对象是否正确。' })
  }
  return items
})

function close() { emit('update:modelValue', false) }

async function submit() {
  loading.value = true
  error.value = ''
  try {
    result.value = await api.compareData(props.connectionId, sourceDatabase.value, sourceTable.value, targetDatabase.value, targetTable.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '对比失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.wide { width: 560px; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; }
.dialog-body { padding: 16px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 10px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.result-panel { margin-top: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }
.metric-card { border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; background: var(--bg-secondary); }
.metric-card span { display: block; font-size: 11px; color: var(--text-muted); }
.metric-card strong { display: block; font-size: 20px; margin: 4px 0; color: var(--accent); }
.metric-card small { font-size: 10px; color: var(--text-muted); }
.metric-card.diff.match strong { color: #1a7f37; }
.metric-card.diff.warn strong { color: var(--danger); }
.compare-bar { display: flex; height: 28px; border-radius: var(--radius); overflow: hidden; margin-bottom: 10px; font-size: 10px; color: #fff; }
.bar-segment { display: flex; align-items: center; justify-content: center; min-width: 40px; }
.bar-segment.source { background: #3370ff; }
.bar-segment.target { background: #52c41a; }
.summary-box { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; font-size: 12px; margin-bottom: 8px; }
.insights { display: flex; flex-direction: column; gap: 4px; }
.insight { font-size: 11px; padding: 6px 8px; border-radius: var(--radius); background: var(--bg-secondary); }
.insight.good { border-left: 3px solid var(--success); }
.insight.warn { border-left: 3px solid var(--warning); }
.insight.info { border-left: 3px solid var(--accent); }
.dialog-footer { display: flex; justify-content: flex-end; margin-top: 10px; }
.error { color: var(--danger); font-size: 12px; }
</style>
