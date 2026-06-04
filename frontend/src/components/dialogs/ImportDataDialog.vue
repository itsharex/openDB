<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header"><h2>导入数据 (CSV)</h2><button class="close-btn" @click="close">×</button></div>
      <div class="dialog-tabs">
        <button class="tab" :class="{ active: viewTab === 'import' }" @click="viewTab = 'import'">导入</button>
        <button class="tab" :class="{ active: viewTab === 'analysis' }" @click="viewTab = 'analysis'">智能分析</button>
      </div>
      <form class="dialog-body" @submit.prevent="submit">
        <template v-if="viewTab === 'import'">
          <label>目标表<input v-model="table" required /></label>
          <label class="checkbox"><input v-model="hasHeader" type="checkbox" /> 首行为列名</label>
          <label>CSV 内容<textarea v-model="csvContent" rows="8" placeholder="id,name&#10;1,Alice" required /></label>
        </template>

        <template v-else>
          <div v-if="!csvAnalytics" class="empty">请先粘贴或选择 CSV 文件</div>
          <template v-else>
            <div class="metric-grid">
              <div class="metric"><span>数据行</span><b>{{ csvAnalytics.rowCount }}</b></div>
              <div class="metric"><span>列数</span><b>{{ csvAnalytics.columnCount }}</b></div>
              <div class="metric"><span>重复行</span><b>{{ csvAnalytics.duplicateRowCount }}</b></div>
              <div class="metric"><span>空行</span><b>{{ csvAnalytics.emptyRowCount }}</b></div>
            </div>
            <div v-for="(item, i) in csvAnalytics.insights" :key="i" class="insight" :class="item.level">
              <strong>{{ item.title }}</strong> — {{ item.detail }}
            </div>
            <table class="type-table">
              <thead>
                <tr><th>列名</th><th>推断类型</th><th>建议 SQL 类型</th><th>空值率</th><th>去重</th><th>样例</th></tr>
              </thead>
              <tbody>
                <tr v-for="col in csvAnalytics.columns" :key="col.name">
                  <td>{{ col.name }}</td>
                  <td>{{ typeLabel(col.inferredType) }}</td>
                  <td><code>{{ formatSuggestedSqlType(col) }}</code></td>
                  <td>{{ col.nullRate.toFixed(1) }}%</td>
                  <td>{{ col.distinctCount }}</td>
                  <td class="samples">{{ col.sampleValues.join(', ') || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <button type="button" class="btn btn-secondary btn-sm" @click="copyCreateTable">复制建表建议</button>
          </template>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="dialog-footer">
          <button type="button" class="btn btn-secondary" @click="pickFile">选择文件</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">{{ submitting ? '导入中...' : '导入' }}</button>
        </div>
      </form>
      <input ref="fileRef" type="file" accept=".csv,.txt" hidden @change="onFile" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { analyzeCsv, formatSuggestedSqlType, typeLabel } from '@/utils/csvAnalytics'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string; defaultTable?: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; imported: [count: number] }>()

const table = ref('')
const csvContent = ref('')
const hasHeader = ref(true)
const submitting = ref(false)
const error = ref('')
const viewTab = ref<'import' | 'analysis'>('import')
const fileRef = ref<HTMLInputElement | null>(null)

const csvAnalytics = computed(() => analyzeCsv(csvContent.value, hasHeader.value))

watch(() => props.modelValue, v => {
  if (v) {
    error.value = ''
    table.value = props.defaultTable ?? ''
    viewTab.value = 'import'
  }
})

function close() { emit('update:modelValue', false) }
function pickFile() { fileRef.value?.click() }
function onFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) file.text().then(t => { csvContent.value = t; viewTab.value = 'analysis' })
}

function copyCreateTable() {
  if (!csvAnalytics.value || !table.value) return
  const cols = csvAnalytics.value.columns
    .map(col => `  \`${col.name}\` ${formatSuggestedSqlType(col)}${col.nullRate < 100 ? '' : ' NOT NULL'}`)
    .join(',\n')
  const ddl = `-- 基于 CSV 推断的建表建议\nCREATE TABLE \`${table.value}\` (\n${cols}\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`
  navigator.clipboard.writeText(ddl)
}

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    const { imported } = await api.importCsv(props.connectionId, props.database, table.value, csvContent.value, hasHeader.value)
    emit('imported', imported)
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '导入失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.wide { width: 640px; max-height: 85vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; }
.dialog-tabs { display: flex; gap: 4px; padding: 8px 12px 0; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.tab { border: none; background: transparent; padding: 6px 12px; font-size: 12px; color: var(--text-secondary); border-bottom: 2px solid transparent; }
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 10px; overflow: auto; flex: 1; min-height: 0; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.checkbox { flex-direction: row; align-items: center; gap: 8px; color: var(--text-primary); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; flex-shrink: 0; }
.error { margin: 0; color: var(--danger); font-size: 12px; }
textarea { font-family: monospace; font-size: 11px; }
.empty { padding: 20px; text-align: center; color: var(--text-muted); font-size: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 8px; }
.metric { border: 1px solid var(--border); border-radius: var(--radius); padding: 8px; font-size: 11px; background: var(--bg-secondary); }
.metric span { display: block; color: var(--text-muted); }
.type-table { width: 100%; border-collapse: collapse; font-size: 11px; margin: 8px 0; }
.type-table th, .type-table td { border: 1px solid var(--border); padding: 4px 6px; text-align: left; }
.type-table th { background: var(--bg-tertiary); }
.samples { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.insight { font-size: 11px; padding: 5px 8px; margin-bottom: 4px; border-radius: var(--radius); background: var(--bg-secondary); }
.insight.good { border-left: 3px solid var(--success); }
.insight.warn { border-left: 3px solid var(--warning); }
.insight.info { border-left: 3px solid var(--accent); }
.btn-sm { padding: 4px 10px; font-size: 11px; }
</style>
