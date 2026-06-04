<template>
  <div v-if="selection" class="detail-panel">
    <div class="detail-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
      <div class="spacer" />
      <button class="btn btn-ghost" title="复制" @click="copyContent">复制</button>
      <button class="btn btn-ghost" title="在编辑器打开" @click="$emit('open-in-editor', editorContent)">打开</button>
    </div>
    <div class="detail-content scrollbar">
      <div v-if="loading" class="state">加载中...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <pre v-else-if="activeTab === 'ddl'">{{ ddl }}</pre>
      <table v-else-if="activeTab === 'columns' && columns.length">
        <thead><tr><th>字段</th><th>类型</th><th>键</th><th>可空</th><th>默认值</th></tr></thead>
        <tbody>
          <tr v-for="col in columns" :key="col.name">
            <td>{{ col.name }}</td><td>{{ col.type }}</td><td>{{ col.key }}</td><td>{{ col.nullable }}</td><td>{{ col.defaultValue ?? '' }}</td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="activeTab === 'indexes' && indexes.length">
        <thead><tr><th>索引名</th><th>列</th><th>唯一</th><th>类型</th></tr></thead>
        <tbody>
          <tr v-for="(idx, i) in indexes" :key="i">
            <td>{{ idx.name }}</td><td>{{ idx.columnName }}</td><td>{{ idx.unique ? '是' : '否' }}</td><td>{{ idx.type }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="activeTab === 'health' && schemaAnalytics" class="health-panel">
        <div class="score-card">
          <div class="score-ring" :class="scoreClass">{{ schemaAnalytics.healthScore }}</div>
          <div>
            <strong>结构健康度 · {{ healthScoreLabel(schemaAnalytics.healthScore) }}</strong>
            <p>{{ selection?.objectName }} · {{ schemaAnalytics.columnCount }} 列 · {{ schemaAnalytics.indexCount }} 索引</p>
          </div>
        </div>
        <div class="metric-grid">
          <div class="metric"><span>主键</span><b>{{ schemaAnalytics.hasPrimaryKey ? schemaAnalytics.primaryKeyColumns.join(', ') : '无' }}</b></div>
          <div class="metric"><span>可空列</span><b>{{ schemaAnalytics.nullableColumnCount }} ({{ schemaAnalytics.nullableRate.toFixed(0) }}%)</b></div>
          <div class="metric"><span>唯一索引</span><b>{{ schemaAnalytics.uniqueIndexCount }}</b></div>
          <div class="metric"><span>已索引列</span><b>{{ schemaAnalytics.indexedColumnCount }}</b></div>
        </div>
        <div class="insight-list">
          <div v-for="(item, i) in schemaAnalytics.insights" :key="i" class="insight" :class="item.level">
            <strong>{{ item.title }}</strong>
            <p>{{ item.detail }}</p>
          </div>
        </div>
      </div>
      <div v-else-if="activeTab === 'suggest' && schemaAnalytics" class="health-panel">
        <div v-if="schemaAnalytics.unindexedColumns.length" class="block">
          <h4>可考虑加索引的列</h4>
          <div class="tag-list">
            <span v-for="col in schemaAnalytics.unindexedColumns" :key="col" class="tag">{{ col }}</span>
          </div>
        </div>
        <div v-if="schemaAnalytics.textHeavyColumns.length" class="block">
          <h4>大文本/字符列</h4>
          <div class="tag-list">
            <span v-for="col in schemaAnalytics.textHeavyColumns" :key="col" class="tag muted">{{ col }}</span>
          </div>
        </div>
        <div class="block">
          <h4>推荐 SQL 片段</h4>
          <pre class="snippet">{{ suggestedSql }}</pre>
          <button class="btn btn-secondary btn-sm" @click="copySuggested">复制建议 SQL</button>
        </div>
      </div>
      <div v-else class="state">暂无数据</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { ColumnInfo, IndexInfo } from '@/types'
import type { ObjectSelection } from '@/types/features'
import { analyzeTableSchema, healthScoreLabel } from '@/utils/schemaAnalytics'

const props = defineProps<{ selection: ObjectSelection | null }>()
defineEmits<{ 'open-in-editor': [sql: string] }>()

const activeTab = ref('ddl')
const loading = ref(false)
const error = ref('')
const ddl = ref('')
const columns = ref<ColumnInfo[]>([])
const indexes = ref<IndexInfo[]>([])

const schemaAnalytics = computed(() => {
  if (!columns.value.length) return null
  return analyzeTableSchema(columns.value, indexes.value)
})

const scoreClass = computed(() => {
  const s = schemaAnalytics.value?.healthScore ?? 0
  if (s >= 85) return 'good'
  if (s >= 70) return 'ok'
  if (s >= 50) return 'mid'
  return 'bad'
})

const suggestedSql = computed(() => {
  if (!props.selection || !schemaAnalytics.value) return ''
  const table = props.selection.objectName
  const lines: string[] = [`-- ${table} 结构优化建议`]
  if (!schemaAnalytics.value.hasPrimaryKey) {
    lines.push(`-- ALTER TABLE \`${table}\` ADD PRIMARY KEY (id);`)
  }
  for (const col of schemaAnalytics.value.unindexedColumns.slice(0, 3)) {
    lines.push(`-- CREATE INDEX idx_${table}_${col} ON \`${table}\` (\`${col}\`);`)
  }
  lines.push('', `SELECT COUNT(*) AS total_rows FROM \`${table}\`;`)
  lines.push(`SELECT * FROM \`${table}\` LIMIT 100;`)
  return lines.join('\n')
})

const tabs = computed(() => {
  if (!props.selection) return []
  if (props.selection.objectType === 'table') {
    return [
      { id: 'ddl', label: 'DDL' },
      { id: 'columns', label: '字段' },
      { id: 'indexes', label: '索引' },
      { id: 'health', label: '健康度' },
      { id: 'suggest', label: '优化建议' }
    ]
  }
  return [{ id: 'ddl', label: 'DDL' }]
})

const editorContent = computed(() => {
  if (activeTab.value === 'ddl') return ddl.value
  if (activeTab.value === 'suggest') return suggestedSql.value
  if (activeTab.value === 'columns' && props.selection?.objectType === 'table') {
    return `SELECT * FROM \`${props.selection.objectName}\` LIMIT 100;`
  }
  return ddl.value
})

watch(() => props.selection, load, { immediate: true })

async function load() {
  if (!props.selection) return
  loading.value = true
  error.value = ''
  activeTab.value = 'ddl'
  try {
    const { connectionId, database, objectName, objectType } = props.selection
    if (objectType === 'table') {
      const [ddlRes, cols, idxs] = await Promise.all([
        api.showCreateTable(connectionId, database, objectName),
        api.listColumns(connectionId, database, objectName),
        api.listIndexes(connectionId, database, objectName)
      ])
      ddl.value = ddlRes.ddl
      columns.value = cols
      indexes.value = idxs
    } else if (objectType === 'view') {
      ddl.value = (await api.showCreateView(connectionId, database, objectName)).ddl
      columns.value = []
      indexes.value = []
    } else {
      ddl.value = `-- ${objectType}: ${objectName}`
      columns.value = []
      indexes.value = []
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function copyContent() {
  navigator.clipboard.writeText(editorContent.value)
}

function copySuggested() {
  navigator.clipboard.writeText(suggestedSql.value)
}
</script>

<style scoped>
.detail-panel { height: 180px; display: flex; flex-direction: column; border-top: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.detail-tabs { display: flex; align-items: center; gap: 4px; padding: 4px 8px; border-bottom: 1px solid var(--border); background: var(--bg-tertiary); }
.tab { border: none; background: transparent; padding: 4px 10px; font-size: 11px; color: var(--text-secondary); border-radius: var(--radius); }
.tab.active { background: var(--bg-primary); color: var(--accent); border: 1px solid var(--border); }
.spacer { flex: 1; }
.detail-content { flex: 1; overflow: auto; padding: 8px; }
pre { margin: 0; font-size: 11px; font-family: monospace; white-space: pre-wrap; }
.state { padding: 16px; text-align: center; color: var(--text-muted); font-size: 12px; }
.state.error { color: var(--danger); }
table { width: 100%; border-collapse: collapse; font-size: 11px; }
th, td { border: 1px solid var(--border); padding: 4px 8px; text-align: left; }
th { background: var(--bg-tertiary); }
.health-panel { display: flex; flex-direction: column; gap: 10px; }
.score-card { display: flex; align-items: center; gap: 12px; padding: 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-primary); }
.score-ring { width: 44px; height: 44px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; border: 3px solid var(--accent); }
.score-ring.good { border-color: var(--success); color: #1a7f37; }
.score-ring.ok { border-color: var(--accent); color: var(--accent); }
.score-ring.mid { border-color: var(--warning); color: #b8860b; }
.score-ring.bad { border-color: var(--danger); color: var(--danger); }
.score-card p { margin: 4px 0 0; font-size: 11px; color: var(--text-muted); }
.metric-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; }
.metric { border: 1px solid var(--border); border-radius: var(--radius); padding: 6px 8px; font-size: 11px; background: var(--bg-primary); }
.metric span { display: block; color: var(--text-muted); margin-bottom: 2px; }
.insight-list { display: flex; flex-direction: column; gap: 6px; }
.insight { border-left: 3px solid var(--border); padding: 6px 8px; font-size: 11px; background: var(--bg-primary); border-radius: 0 var(--radius) var(--radius) 0; }
.insight strong { display: block; margin-bottom: 2px; }
.insight p { margin: 0; color: var(--text-secondary); }
.insight.good { border-left-color: var(--success); }
.insight.info { border-left-color: var(--accent); }
.insight.warn { border-left-color: var(--warning); }
.block h4 { margin: 0 0 6px; font-size: 11px; color: var(--text-secondary); }
.tag-list { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.tag { font-size: 10px; padding: 2px 6px; border-radius: 999px; background: var(--accent-light); color: var(--accent); }
.tag.muted { background: var(--bg-tertiary); color: var(--text-secondary); }
.snippet { background: var(--bg-tertiary); padding: 8px; border-radius: var(--radius); margin-bottom: 6px; }
.btn-sm { padding: 3px 8px; font-size: 11px; }
</style>
