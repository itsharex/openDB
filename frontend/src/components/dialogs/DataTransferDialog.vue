<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header"><h2>数据传输</h2><button class="close-btn" @click="close">×</button></div>
      <form class="dialog-body" @submit.prevent="submit">
        <div class="grid-2">
          <label>源数据库<input v-model="sourceDatabase" required /></label>
          <label>源表<input v-model="sourceTable" required /></label>
          <label>目标数据库<input v-model="targetDatabase" required /></label>
          <label>目标表<input v-model="targetTable" required /></label>
        </div>
        <label class="checkbox"><input v-model="truncateTarget" type="checkbox" /> 传输前清空目标表</label>

        <div class="action-row">
          <button type="button" class="btn btn-secondary btn-sm" :disabled="previewing || !canPreview" @click="runPreview">
            {{ previewing ? '预检中...' : '预检分析' }}
          </button>
          <span v-if="preview" class="preview-hint">{{ preview.estimatedImpact }}</span>
        </div>

        <div v-if="preview" class="preview-panel">
          <div class="metric-grid">
            <div class="metric"><span>源表行数</span><b>{{ preview.sourceRows.toLocaleString() }}</b></div>
            <div class="metric"><span>目标表行数</span><b>{{ preview.targetRows.toLocaleString() }}</b></div>
            <div class="metric"><span>行数差</span><b>{{ preview.rowDiff >= 0 ? '+' : '' }}{{ preview.rowDiff.toLocaleString() }}</b></div>
          </div>
          <div v-for="(item, i) in preview.insights" :key="i" class="insight" :class="item.level">
            <strong>{{ item.title }}</strong> — {{ item.detail }}
          </div>
        </div>

        <div v-if="phase !== 'idle' && phase !== 'done'" class="progress-panel">
          <div class="progress-head">
            <span>{{ phaseLabel(phase) }}</span>
            <span>{{ progressPct }}%</span>
          </div>
          <div class="progress-track"><div class="progress-fill" :style="{ width: `${progressPct}%` }" /></div>
        </div>

        <div v-if="resultStats" class="result-panel">
          <div class="metric-grid">
            <div class="metric good"><span>已传输</span><b>{{ resultStats.transferred.toLocaleString() }} 行</b></div>
            <div class="metric"><span>耗时</span><b>{{ (resultStats.durationMs / 1000).toFixed(2) }}s</b></div>
            <div class="metric"><span>速率</span><b>{{ formatSpeed(resultStats.rowsPerSecond) }}</b></div>
          </div>
          <div v-for="(item, i) in resultStats.insights" :key="i" class="insight" :class="item.level">
            <strong>{{ item.title }}</strong> — {{ item.detail }}
          </div>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="dialog-footer">
          <button type="button" class="btn btn-secondary" @click="close">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">{{ submitting ? '传输中...' : '开始传输' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import {
  buildTransferPreview,
  buildTransferResultStats,
  phaseLabel,
  type TransferPhase,
  type TransferPreview
} from '@/utils/transferAnalytics'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string; table?: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; done: [rows: number] }>()

const sourceDatabase = ref('')
const sourceTable = ref('')
const targetDatabase = ref('')
const targetTable = ref('')
const truncateTarget = ref(false)
const submitting = ref(false)
const previewing = ref(false)
const error = ref('')
const preview = ref<TransferPreview | null>(null)
const resultStats = ref<ReturnType<typeof buildTransferResultStats> | null>(null)
const phase = ref<TransferPhase>('idle')
const progressPct = ref(0)

const canPreview = computed(() =>
  !!(sourceDatabase.value && sourceTable.value && targetDatabase.value && targetTable.value)
)

watch(() => props.modelValue, v => {
  if (v) {
    sourceDatabase.value = props.database
    sourceTable.value = props.table ?? ''
    targetDatabase.value = props.database
    targetTable.value = props.table ?? ''
    error.value = ''
    preview.value = null
    resultStats.value = null
    phase.value = 'idle'
    progressPct.value = 0
  }
})

function close() { emit('update:modelValue', false) }

function formatSpeed(rps: number) {
  if (rps <= 0) return '-'
  return rps >= 1000 ? `${(rps / 1000).toFixed(1)}k 行/秒` : `${Math.round(rps)} 行/秒`
}

async function runPreview() {
  previewing.value = true
  error.value = ''
  phase.value = 'preview'
  progressPct.value = 20
  try {
    const compare = await api.compareData(
      props.connectionId,
      sourceDatabase.value,
      sourceTable.value,
      targetDatabase.value,
      targetTable.value
    )
    preview.value = buildTransferPreview(compare, truncateTarget.value)
    phase.value = 'idle'
    progressPct.value = 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : '预检失败'
    phase.value = 'error'
  } finally {
    previewing.value = false
  }
}

async function submit() {
  submitting.value = true
  error.value = ''
  resultStats.value = null
  phase.value = 'transferring'
  progressPct.value = 30
  const start = performance.now()
  try {
    if (!preview.value) await runPreview()
    progressPct.value = 55
    const { transferred } = await api.transferData(props.connectionId, {
      sourceDatabase: sourceDatabase.value,
      sourceTable: sourceTable.value,
      targetDatabase: targetDatabase.value,
      targetTable: targetTable.value,
      truncateTarget: truncateTarget.value
    })
    progressPct.value = 100
    phase.value = 'done'
    const durationMs = performance.now() - start
    resultStats.value = buildTransferResultStats(
      transferred,
      durationMs,
      truncateTarget.value,
      `${sourceDatabase.value}.${sourceTable.value}`,
      `${targetDatabase.value}.${targetTable.value}`
    )
    emit('done', transferred)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '传输失败'
    phase.value = 'error'
  } finally {
    submitting.value = false
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
.checkbox { flex-direction: row; align-items: center; gap: 8px; color: var(--text-primary); margin-top: 8px; }
.action-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.preview-hint { font-size: 11px; color: var(--accent); }
.preview-panel, .result-panel { margin-top: 10px; padding: 10px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-secondary); }
.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
.metric { border: 1px solid var(--border); border-radius: var(--radius); padding: 8px; font-size: 11px; background: var(--bg-primary); }
.metric span { display: block; color: var(--text-muted); margin-bottom: 2px; }
.metric.good b { color: #1a7f37; }
.insight { font-size: 11px; padding: 5px 8px; margin-top: 4px; border-radius: var(--radius); background: var(--bg-primary); }
.insight.good { border-left: 3px solid var(--success); }
.insight.warn { border-left: 3px solid var(--warning); }
.insight.info { border-left: 3px solid var(--accent); }
.progress-panel { margin-top: 10px; }
.progress-head { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }
.progress-track { height: 8px; background: var(--bg-tertiary); border-radius: 999px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent); transition: width 0.3s; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.error { color: var(--danger); font-size: 12px; }
.btn-sm { padding: 4px 10px; font-size: 11px; }
</style>
