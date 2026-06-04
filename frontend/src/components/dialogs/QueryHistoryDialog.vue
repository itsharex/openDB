<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header">
        <h2>查询历史</h2>
        <div class="header-actions">
          <button class="btn btn-ghost" @click="clear">清空</button>
          <button class="close-btn" @click="close">×</button>
        </div>
      </div>
      <div class="dialog-tabs">
        <button class="tab" :class="{ active: viewTab === 'list' }" @click="viewTab = 'list'">历史记录</button>
        <button class="tab" :class="{ active: viewTab === 'stats' }" @click="viewTab = 'stats'">统计分析</button>
      </div>
      <div class="dialog-body scrollbar">
        <template v-if="viewTab === 'list'">
          <div class="toolbar">
            <input v-model="filterText" class="filter" placeholder="搜索 SQL、库名..." />
            <select v-model="filterStatus" class="filter-select">
              <option value="all">全部状态</option>
              <option value="success">仅成功</option>
              <option value="fail">仅失败</option>
            </select>
          </div>
          <div v-if="filteredItems.length === 0" class="empty">暂无匹配的查询历史</div>
          <button
            v-for="item in filteredItems"
            :key="item.id"
            class="history-item"
            @click="select(item.sql)"
          >
            <div class="history-meta">
              <span class="sql-type">{{ classifySql(item.sql) }}</span>
              <span :class="item.success ? 'ok' : 'fail'">{{ item.success ? '成功' : '失败' }}</span>
              <span>{{ formatTime(item.executedAt) }}</span>
              <span v-if="item.durationMs">{{ item.durationMs }}ms</span>
              <span v-if="item.rowCount != null">{{ item.rowCount }} 行</span>
              <span v-if="item.database">{{ item.database }}</span>
            </div>
            <pre>{{ item.sql }}</pre>
          </button>
        </template>

        <template v-else>
          <div v-if="stats.total === 0" class="empty">暂无数据，执行查询后将生成统计</div>
          <template v-else>
            <div class="metric-grid">
              <div class="metric-card"><span>总查询</span><strong>{{ stats.total }}</strong></div>
              <div class="metric-card"><span>成功率</span><strong>{{ stats.successRate.toFixed(1) }}%</strong></div>
              <div class="metric-card"><span>平均耗时</span><strong>{{ stats.avgDurationMs.toFixed(0) }}ms</strong></div>
              <div class="metric-card"><span>返回行数</span><strong>{{ stats.totalRowsReturned.toLocaleString() }}</strong></div>
            </div>

            <div v-if="stats.insights.length" class="block">
              <h3>洞察</h3>
              <div v-for="(item, i) in stats.insights" :key="i" class="insight" :class="item.level">
                <strong>{{ item.title }}</strong> — {{ item.detail }}
              </div>
            </div>

            <div class="split">
              <div class="block">
                <h3>高频关键词</h3>
                <div v-for="kw in stats.topKeywords" :key="kw.word" class="bar-row">
                  <span>{{ kw.word }}</span>
                  <div class="bar-track"><div class="bar-fill" :style="{ width: `${(kw.count / maxKeywordCount) * 100}%` }" /></div>
                  <span class="count">{{ kw.count }}</span>
                </div>
              </div>
              <div class="block">
                <h3>按数据库</h3>
                <div v-for="db in stats.byDatabase" :key="db.name" class="bar-row">
                  <span>{{ db.name }}</span>
                  <div class="bar-track"><div class="bar-fill accent" :style="{ width: `${(db.count / stats.total) * 100}%` }" /></div>
                  <span class="count">{{ db.count }}</span>
                </div>
              </div>
            </div>

            <div v-if="stats.slowQueries.length" class="block">
              <h3>慢查询 (≥1s)</h3>
              <button
                v-for="item in stats.slowQueries"
                :key="item.id"
                class="history-item compact"
                @click="select(item.sql)"
              >
                <div class="history-meta">
                  <span class="fail">{{ item.durationMs }}ms</span>
                  <span>{{ formatTime(item.executedAt) }}</span>
                </div>
                <pre>{{ item.sql }}</pre>
              </button>
            </div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { clearQueryHistory, loadQueryHistory } from '@/composables/useQueryHistory'
import { analyzeQueryHistory, classifySql } from '@/utils/queryHistoryAnalytics'
import type { QueryHistoryItem } from '@/types/features'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; select: [sql: string] }>()

const items = ref<QueryHistoryItem[]>([])
const viewTab = ref<'list' | 'stats'>('list')
const filterText = ref('')
const filterStatus = ref<'all' | 'success' | 'fail'>('all')

watch(() => props.modelValue, v => {
  if (v) {
    items.value = loadQueryHistory()
    viewTab.value = 'list'
    filterText.value = ''
    filterStatus.value = 'all'
  }
})

const stats = computed(() => analyzeQueryHistory(items.value))

const maxKeywordCount = computed(() => Math.max(...stats.value.topKeywords.map(k => k.count), 1))

const filteredItems = computed(() => {
  const keyword = filterText.value.trim().toLowerCase()
  return items.value.filter(item => {
    if (filterStatus.value === 'success' && !item.success) return false
    if (filterStatus.value === 'fail' && item.success) return false
    if (!keyword) return true
    return (
      item.sql.toLowerCase().includes(keyword) ||
      (item.database ?? '').toLowerCase().includes(keyword) ||
      (item.connectionName ?? '').toLowerCase().includes(keyword)
    )
  })
})

function close() {
  emit('update:modelValue', false)
}

function select(sql: string) {
  emit('select', sql)
  close()
}

function clear() {
  clearQueryHistory()
  items.value = []
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleString()
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.wide { width: 720px; max-height: 78vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-tabs { display: flex; gap: 4px; padding: 8px 12px 0; border-bottom: 1px solid var(--border); }
.tab { border: none; background: transparent; padding: 6px 12px; font-size: 12px; color: var(--text-secondary); border-bottom: 2px solid transparent; }
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }
.dialog-body { padding: 10px 12px; overflow: auto; flex: 1; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
.filter { flex: 1; font-size: 12px; padding: 5px 8px; }
.filter-select { font-size: 12px; padding: 5px 6px; }
.empty { padding: 24px; text-align: center; color: var(--text-muted); font-size: 12px; }
.history-item { width: 100%; text-align: left; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-primary); padding: 8px 10px; margin-bottom: 6px; }
.history-item.compact { margin-bottom: 4px; }
.history-item:hover { border-color: var(--accent); background: var(--accent-light); }
.history-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: var(--text-muted); margin-bottom: 4px; }
.sql-type { color: var(--accent); font-weight: 600; }
.ok { color: #1a7f37; }
.fail { color: var(--danger); }
pre { margin: 0; font-size: 11px; font-family: monospace; white-space: pre-wrap; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
.metric-card { border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; background: var(--bg-secondary); }
.metric-card span { display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px; }
.metric-card strong { font-size: 18px; color: var(--accent); }
.block { margin-bottom: 12px; }
.block h3 { margin: 0 0 8px; font-size: 12px; color: var(--text-secondary); }
.insight { font-size: 11px; padding: 6px 8px; border-radius: var(--radius); margin-bottom: 4px; background: var(--bg-secondary); }
.insight.warn { border-left: 3px solid var(--warning); }
.insight.good { border-left: 3px solid var(--success); }
.insight.info { border-left: 3px solid var(--accent); }
.split { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.bar-row { display: grid; grid-template-columns: 80px 1fr 32px; gap: 6px; align-items: center; margin-bottom: 6px; font-size: 11px; }
.bar-track { height: 8px; background: var(--bg-tertiary); border-radius: 999px; overflow: hidden; }
.bar-fill { height: 100%; background: #91caff; border-radius: 999px; }
.bar-fill.accent { background: var(--accent); }
.count { text-align: right; color: var(--text-muted); }
</style>
