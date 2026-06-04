<template>
  <div class="analytics-panel">
    <div v-if="!hasAnalysis" class="state">暂无可分析的数据</div>
    <template v-else>
      <div class="analytics-toolbar">
        <div class="tab-list">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="tab-btn"
            :class="{ active: activeTab === tab.id }"
            @click="activeTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </div>
        <button class="btn btn-secondary btn-sm" title="复制分析报告" @click="copyReport">复制报告</button>
      </div>

      <div class="analytics-body scrollbar">
        <section v-if="activeTab === 'overview' && analytics" class="section">
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">行数</span>
              <strong>{{ analytics.rowCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">列数</span>
              <strong>{{ analytics.columnCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">数值列</span>
              <strong>{{ analytics.numericColumnCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">完整行率</span>
              <strong>{{ analytics.completeRowRate.toFixed(1) }}%</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">重复行率</span>
              <strong>{{ analytics.duplicateRate.toFixed(1) }}%</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">文本列</span>
              <strong>{{ analytics.stringColumnCount }}</strong>
            </div>
          </div>

          <div v-if="analytics.insights.length" class="block">
            <h3>智能洞察</h3>
            <div class="insight-list">
              <div
                v-for="(item, index) in analytics.insights"
                :key="index"
                class="insight-item"
                :class="item.level"
              >
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </div>

          <div v-if="strongCorrelations.length" class="block">
            <h3>显著相关性</h3>
            <div class="corr-list">
              <div v-for="item in strongCorrelations" :key="`${item.colA}-${item.colB}`" class="corr-item">
                <span>{{ item.colA }} ↔ {{ item.colB }}</span>
                <span class="corr-value" :class="item.coefficient >= 0 ? 'pos' : 'neg'">
                  {{ item.coefficient.toFixed(3) }} · {{ correlationLabel(item.coefficient) }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'columns' && analytics" class="section">
          <div class="column-cards">
            <article v-for="col in analytics.columns" :key="col.name" class="column-card">
              <header>
                <div>
                  <strong>{{ col.name }}</strong>
                  <span class="kind-tag">{{ kindLabel(col.kind) }}</span>
                </div>
                <span class="mini-meta">唯一率 {{ col.uniqueRate.toFixed(1) }}%</span>
              </header>
              <div class="stat-grid">
                <div><span>非空</span><b>{{ col.count }}</b></div>
                <div><span>空值</span><b>{{ col.nullCount }} ({{ col.nullRate.toFixed(1) }}%)</b></div>
                <div><span>去重</span><b>{{ col.distinctCount }}</b></div>
                <div v-if="col.mean != null"><span>均值</span><b>{{ formatMetric(col.mean) }}</b></div>
                <div v-if="col.median != null"><span>中位数</span><b>{{ formatMetric(col.median) }}</b></div>
                <div v-if="col.stdDev != null"><span>标准差</span><b>{{ formatMetric(col.stdDev) }}</b></div>
                <div v-if="col.min != null"><span>最小</span><b>{{ formatMetric(col.min) }}</b></div>
                <div v-if="col.max != null"><span>最大</span><b>{{ formatMetric(col.max) }}</b></div>
                <div v-if="col.sum != null"><span>合计</span><b>{{ formatMetric(col.sum) }}</b></div>
                <div v-if="col.q1 != null"><span>Q1</span><b>{{ formatMetric(col.q1) }}</b></div>
                <div v-if="col.q3 != null"><span>Q3</span><b>{{ formatMetric(col.q3) }}</b></div>
                <div v-if="col.avgLength != null"><span>平均长度</span><b>{{ col.avgLength!.toFixed(1) }}</b></div>
                <div v-if="col.outlierCount"><span>离群点</span><b>{{ col.outlierCount }}</b></div>
              </div>
              <div v-if="col.topValues.length" class="freq-block">
                <span class="sub-title">高频值 TOP</span>
                <div v-for="item in col.topValues.slice(0, 5)" :key="item.value" class="freq-row">
                  <span class="freq-label" :title="item.value">{{ item.value }}</span>
                  <div class="bar-track"><div class="bar-fill" :style="{ width: `${item.pct}%` }" /></div>
                  <span class="freq-count">{{ item.count }} ({{ item.pct.toFixed(1) }}%)</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeTab === 'distribution' && analytics" class="section">
          <div v-if="numericColumns.length === 0" class="state inline">没有可绘制的数值列</div>
          <article v-for="col in numericColumns" :key="col.name" class="chart-card">
            <header>
              <strong>{{ col.name }}</strong>
              <span>均值 {{ formatMetric(col.mean) }} · 中位数 {{ formatMetric(col.median) }}</span>
            </header>
            <div class="histogram">
              <div v-for="bin in col.histogram" :key="bin.label" class="hist-row">
                <span class="hist-label">{{ bin.label }}</span>
                <div class="bar-track wide"><div class="bar-fill accent" :style="{ width: `${bin.pct}%` }" /></div>
                <span class="hist-count">{{ bin.count }}</span>
              </div>
            </div>
          </article>

          <div v-if="textColumns.length" class="block">
            <h3>文本列 TOP 值分布</h3>
            <article v-for="col in textColumns" :key="col.name" class="chart-card compact">
              <header><strong>{{ col.name }}</strong></header>
              <div v-for="item in col.topValues.slice(0, 6)" :key="item.value" class="freq-row">
                <span class="freq-label" :title="item.value">{{ item.value }}</span>
                <div class="bar-track wide"><div class="bar-fill" :style="{ width: `${item.pct}%` }" /></div>
                <span class="freq-count">{{ item.pct.toFixed(1) }}%</span>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeTab === 'correlation' && analytics" class="section">
          <div v-if="!analytics.correlations.length" class="state inline">数值列之间未发现显著相关性（|r| ≥ 0.35）</div>
          <table v-else class="corr-table">
            <thead>
              <tr>
                <th>列 A</th>
                <th>列 B</th>
                <th>皮尔逊 r</th>
                <th>强度</th>
                <th>相关方向</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in analytics.correlations" :key="`${item.colA}-${item.colB}`">
                <td>{{ item.colA }}</td>
                <td>{{ item.colB }}</td>
                <td :class="item.coefficient >= 0 ? 'pos' : 'neg'">{{ item.coefficient.toFixed(4) }}</td>
                <td>{{ correlationLabel(item.coefficient) }}</td>
                <td>{{ item.coefficient >= 0 ? '正相关' : '负相关' }}</td>
              </tr>
            </tbody>
          </table>
          <p class="hint">基于当前结果集实时计算，样本量较小时仅供参考。</p>
        </section>

        <section v-else-if="activeTab === 'selection' && selectionAnalytics" class="section">
          <p class="hint top">
            基于数据表中选中的 {{ props.selection!.rows.length }} 行 × {{ props.selection!.columns.length }} 列实时分析
          </p>
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">选区行数</span>
              <strong>{{ selectionAnalytics.rowCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">选区列数</span>
              <strong>{{ selectionAnalytics.columnCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">完整行率</span>
              <strong>{{ selectionAnalytics.completeRowRate.toFixed(1) }}%</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">数值列</span>
              <strong>{{ selectionAnalytics.numericColumnCount }}</strong>
            </div>
          </div>
          <div class="column-cards">
            <article v-for="col in selectionAnalytics.columns" :key="col.name" class="column-card compact-card">
              <header>
                <strong>{{ col.name }}</strong>
                <span class="kind-tag">{{ kindLabel(col.kind) }}</span>
              </header>
              <div class="stat-grid">
                <div v-if="col.mean != null"><span>均值</span><b>{{ formatMetric(col.mean) }}</b></div>
                <div v-if="col.sum != null"><span>合计</span><b>{{ formatMetric(col.sum) }}</b></div>
                <div v-if="col.min != null"><span>最小</span><b>{{ formatMetric(col.min) }}</b></div>
                <div v-if="col.max != null"><span>最大</span><b>{{ formatMetric(col.max) }}</b></div>
                <div><span>非空</span><b>{{ col.count }}</b></div>
                <div><span>去重</span><b>{{ col.distinctCount }}</b></div>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeTab === 'quality' && analytics" class="section">
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">整体完整行率</span>
              <strong>{{ analytics!.completeRowRate.toFixed(1) }}%</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">重复行率</span>
              <strong>{{ analytics!.duplicateRate.toFixed(1) }}%</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">高缺失列 (≥30%)</span>
              <strong>{{ qualityColumns.filter(c => c.nullRate >= 30).length }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">常量列</span>
              <strong>{{ qualityColumns.filter(c => c.distinctCount === 1 && c.count > 1).length }}</strong>
            </div>
          </div>

          <div class="block">
            <h3>列完整度排行</h3>
            <div v-for="col in qualityColumns" :key="col.name" class="quality-row">
              <span class="quality-name">{{ col.name }}</span>
              <div class="bar-track wide">
                <div
                  class="bar-fill"
                  :class="col.nullRate >= 30 ? 'warn' : col.nullRate >= 10 ? 'mid' : 'good'"
                  :style="{ width: `${100 - col.nullRate}%` }"
                />
              </div>
              <span class="quality-meta">{{ (100 - col.nullRate).toFixed(1) }}% 完整 · 空值 {{ col.nullRate.toFixed(1) }}%</span>
            </div>
          </div>

          <div v-if="analytics!.insights.length" class="block">
            <h3>质量建议</h3>
            <div class="insight-list">
              <div
                v-for="(item, index) in analytics!.insights.filter(i => i.level !== 'good')"
                :key="index"
                class="insight-item"
                :class="item.level"
              >
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'explain' && explainAnalytics" class="section">
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">计划步骤</span>
              <strong>{{ explainAnalytics.stepCount }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">全表扫描</span>
              <strong :class="explainAnalytics.fullScans.length ? 'warn-text' : 'good-text'">{{ explainAnalytics.fullScans.length }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">索引扫描</span>
              <strong>{{ explainAnalytics.indexScans.length }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">估算总行数</span>
              <strong>{{ explainAnalytics.estimatedRows.toLocaleString() }}</strong>
            </div>
          </div>
          <div v-if="explainAnalytics.insights.length" class="block">
            <h3>执行计划洞察</h3>
            <div class="insight-list">
              <div v-for="(item, index) in explainAnalytics.insights" :key="index" class="insight-item" :class="item.level">
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </div>
          <table v-if="result?.rows?.length" class="explain-table">
            <thead>
              <tr>
                <th v-for="col in result.columns" :key="col">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in result.rows" :key="ri" :class="{ 'row-warn': isWarnStep(row) }">
                <td v-for="col in result.columns" :key="col">{{ formatCell(row[col]) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-else-if="activeTab === 'group' && analytics" class="section">
          <div class="group-controls">
            <label>
              分组字段
              <select v-model="groupColumn">
                <option v-for="col in result?.columns ?? []" :key="col" :value="col">{{ col }}</option>
              </select>
            </label>
            <span class="hint">按选中列分组，并自动汇总其他数值列</span>
          </div>
          <table v-if="groupRows.length" class="group-table">
            <thead>
              <tr>
                <th>{{ groupColumn }}</th>
                <th>计数</th>
                <th v-for="metricCol in groupMetricColumns" :key="metricCol" colspan="2">{{ metricCol }}</th>
              </tr>
              <tr class="sub-head">
                <th colspan="2" />
                <template v-for="metricCol in groupMetricColumns" :key="`${metricCol}-sub`">
                  <th>合计</th>
                  <th>均值</th>
                </template>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in groupRows.slice(0, 100)" :key="row.groupValue">
                <td>{{ row.groupValue }}</td>
                <td>{{ row.count }}</td>
                <template v-for="metricCol in groupMetricColumns" :key="`${row.groupValue}-${metricCol}`">
                  <td>{{ formatMetric(row.metrics[metricCol]?.sum) }}</td>
                  <td>{{ formatMetric(row.metrics[metricCol]?.avg) }}</td>
                </template>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SqlExecuteResponse } from '@/types'
import {
  analyzeResult,
  analyzeSubset,
  buildGroupAggregates,
  correlationLabel,
  formatMetric,
  kindLabel
} from '@/utils/resultAnalytics'
import { analyzeExplain, isExplainResult } from '@/utils/explainAnalytics'

const props = defineProps<{
  result: SqlExecuteResponse | null
  selection?: { rows: Record<string, unknown>[]; columns: string[] } | null
}>()

const emit = defineEmits<{ notify: [message: string, type?: 'success' | 'error' | 'info'] }>()

const baseTabs = [
  { id: 'overview', label: '概览' },
  { id: 'columns', label: '列画像' },
  { id: 'distribution', label: '分布' },
  { id: 'correlation', label: '相关性' },
  { id: 'quality', label: '数据质量' },
  { id: 'group', label: '分组统计' }
] as const

type TabId = typeof baseTabs[number]['id'] | 'selection' | 'explain'

const activeTab = ref<TabId>('overview')
const groupColumn = ref('')

const analytics = computed(() => analyzeResult(props.result))
const explainAnalytics = computed(() => analyzeExplain(props.result))
const hasAnalysis = computed(() => !!(analytics.value || explainAnalytics.value))

const selectionAnalytics = computed(() => {
  if (!props.selection?.rows.length) return null
  return analyzeSubset(props.selection.rows, props.selection.columns)
})

const tabs = computed(() => {
  const list: { id: TabId; label: string }[] = []
  if (explainAnalytics.value) {
    list.push({ id: 'explain', label: '执行计划' })
  }
  if (selectionAnalytics.value) {
    list.push({ id: 'selection', label: '选区分析' })
  }
  if (!isExplainResult(props.result)) {
    list.push(...baseTabs)
  }
  return list
})

const qualityColumns = computed(() =>
  [...(analytics.value?.columns ?? [])].sort((a, b) => b.nullRate - a.nullRate)
)

watch(
  () => [props.result, props.selection] as const,
  ([result, selection]) => {
    if (selection?.rows.length) {
      activeTab.value = 'selection'
    } else if (isExplainResult(result)) {
      activeTab.value = 'explain'
    } else {
      activeTab.value = 'overview'
    }
    groupColumn.value = result?.columns?.[0] ?? ''
  },
  { immediate: true }
)

function formatCell(value: unknown) {
  if (value == null) return ''
  return String(value)
}

function isWarnStep(row: Record<string, unknown>) {
  const type = String(row.type ?? row.TYPE ?? '').toUpperCase()
  const extra = String(row.Extra ?? row.extra ?? '').toLowerCase()
  return type === 'ALL' || extra.includes('filesort') || extra.includes('temporary')
}

const numericColumns = computed(() =>
  analytics.value?.columns.filter(c => c.kind === 'number' || c.kind === 'integer') ?? []
)

const textColumns = computed(() =>
  analytics.value?.columns.filter(c => c.topValues.length && (c.kind === 'string' || c.kind === 'mixed')) ?? []
)

const strongCorrelations = computed(() => analytics.value?.correlations.slice(0, 5) ?? [])

const groupRows = computed(() => buildGroupAggregates(props.result, groupColumn.value))

const groupMetricColumns = computed(() => {
  const first = groupRows.value[0]
  if (!first) return []
  return Object.keys(first.metrics)
})

async function copyReport() {
  if (!analytics.value && !explainAnalytics.value) return
  const lines: string[] = ['# openDB 结果分析报告', '']
  if (explainAnalytics.value) {
    lines.push(
      '## 执行计划',
      `- 步骤: ${explainAnalytics.value.stepCount}`,
      `- 全表扫描: ${explainAnalytics.value.fullScans.length}`,
      `- 估算行数: ${explainAnalytics.value.estimatedRows}`,
      '',
      '### 洞察',
      ...explainAnalytics.value.insights.map(item => `- [${item.level}] ${item.title}: ${item.detail}`)
    )
  }
  if (analytics.value) {
    lines.push(
      `- 行数: ${analytics.value.rowCount}`,
      `- 列数: ${analytics.value.columnCount}`,
      `- 完整行率: ${analytics.value.completeRowRate.toFixed(1)}%`,
      `- 重复行率: ${analytics.value.duplicateRate.toFixed(1)}%`,
      '',
      '## 洞察',
      ...analytics.value.insights.map(item => `- [${item.level}] ${item.title}: ${item.detail}`),
      '',
      '## 列摘要'
    )
    for (const col of analytics.value.columns) {
      lines.push(
        `- ${col.name} (${kindLabel(col.kind)}): 非空 ${col.count}, 空值 ${col.nullRate.toFixed(1)}%, 唯一率 ${col.uniqueRate.toFixed(1)}%` +
        (col.mean != null ? `, 均值 ${formatMetric(col.mean)}` : '')
      )
    }
    if (analytics.value.correlations.length) {
      lines.push('', '## 相关性')
      for (const item of analytics.value.correlations) {
        lines.push(`- ${item.colA} ↔ ${item.colB}: r=${item.coefficient.toFixed(3)} (${correlationLabel(item.coefficient)})`)
      }
    }
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    emit('notify', '分析报告已复制', 'success')
  } catch {
    emit('notify', '复制失败', 'error')
  }
}
</script>

<style scoped>
.analytics-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.analytics-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.tab-btn {
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-secondary);
  padding: 4px 10px;
  border-radius: var(--radius);
  font-size: 12px;
}

.tab-btn.active {
  color: var(--accent);
  background: var(--accent-light);
  border-color: #b3ccff;
}

.analytics-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.state {
  color: var(--text-muted);
  padding: 24px;
  text-align: center;
  font-size: 12px;
}

.state.inline {
  padding: 12px 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.metric-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-primary);
  padding: 10px 12px;
}

.metric-label {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.metric-card strong {
  font-size: 18px;
  color: var(--accent);
}

.block h3,
.section h3 {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.insight-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.insight-item {
  border: 1px solid var(--border);
  border-left-width: 3px;
  border-radius: var(--radius);
  padding: 8px 10px;
  background: var(--bg-primary);
}

.insight-item strong {
  display: block;
  font-size: 12px;
  margin-bottom: 4px;
}

.insight-item p {
  margin: 0;
  font-size: 11px;
  color: var(--text-secondary);
}

.insight-item.good { border-left-color: var(--success); }
.insight-item.info { border-left-color: var(--accent); }
.insight-item.warn { border-left-color: var(--warning); }

.corr-list,
.column-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.corr-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-primary);
}

.corr-value.pos { color: #1a7f37; }
.corr-value.neg { color: var(--danger); }

.column-card,
.chart-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-primary);
  padding: 10px 12px;
}

.column-card header,
.chart-card header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
}

.kind-tag {
  margin-left: 6px;
  font-size: 10px;
  color: var(--accent);
  background: var(--accent-light);
  padding: 1px 6px;
  border-radius: 999px;
}

.mini-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 6px;
  margin-bottom: 8px;
}

.stat-grid div {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 6px 8px;
  font-size: 11px;
}

.stat-grid span {
  display: block;
  color: var(--text-muted);
  margin-bottom: 2px;
}

.freq-block {
  margin-top: 4px;
}

.sub-title {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.freq-row,
.hist-row {
  display: grid;
  grid-template-columns: minmax(80px, 140px) 1fr 72px;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 11px;
}

.freq-label,
.hist-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}

.bar-track {
  height: 8px;
  background: var(--bg-tertiary);
  border-radius: 999px;
  overflow: hidden;
}

.bar-track.wide {
  height: 10px;
}

.bar-fill {
  height: 100%;
  background: #91caff;
  border-radius: 999px;
}

.bar-fill.accent {
  background: var(--accent);
}

.bar-fill.good { background: #52c41a; }
.bar-fill.mid { background: #faad14; }
.bar-fill.warn { background: var(--danger); }

.compact-card header { margin-bottom: 6px; }

.quality-row {
  display: grid;
  grid-template-columns: minmax(80px, 120px) 1fr minmax(140px, 180px);
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 11px;
}

.quality-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}

.quality-meta {
  text-align: right;
  color: var(--text-muted);
}

.hint.top { margin: 0 0 4px; }

.freq-count,
.hist-count {
  text-align: right;
  color: var(--text-muted);
}

.chart-card.compact {
  margin-bottom: 8px;
}

.corr-table,
.group-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.corr-table th,
.corr-table td,
.group-table th,
.group-table td {
  border: 1px solid var(--border);
  padding: 6px 8px;
  text-align: left;
}

.corr-table th,
.group-table th {
  background: var(--bg-tertiary);
}

.group-table .sub-head th {
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 500;
}

.pos { color: #1a7f37; }
.neg { color: var(--danger); }

.group-controls {
  display: flex;
  align-items: end;
  gap: 12px;
  flex-wrap: wrap;
}

.group-controls label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.group-controls select {
  min-width: 180px;
  font-size: 12px;
  padding: 4px 6px;
}

.hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--text-muted);
}

.btn-sm { padding: 3px 8px; font-size: 11px; }

.explain-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.explain-table th,
.explain-table td {
  border: 1px solid var(--border);
  padding: 5px 8px;
  text-align: left;
}

.explain-table th {
  background: var(--bg-tertiary);
}

.explain-table tr.row-warn {
  background: #fff8e6;
}

.warn-text { color: var(--danger); }
.good-text { color: #1a7f37; }
</style>
