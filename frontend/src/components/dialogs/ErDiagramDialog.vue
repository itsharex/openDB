<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog er-dialog">
      <div class="dialog-header">
        <h2>ER 图 — {{ database }}</h2>
        <div class="header-actions">
          <button class="btn btn-ghost btn-sm" :class="{ active: showStats }" @click="showStats = !showStats">统计</button>
          <button class="btn btn-ghost btn-sm" @click="zoomIn">＋</button>
          <button class="btn btn-ghost btn-sm" @click="zoomOut">－</button>
          <button class="close-btn" @click="close">×</button>
        </div>
      </div>
      <div class="dialog-body">
        <div v-if="loading" class="state">加载中...</div>
        <div v-else-if="error" class="state error">{{ error }}</div>
        <div v-else class="body-layout">
          <aside v-if="showStats && erStats" class="stats-panel scrollbar">
            <div class="metric-grid">
              <div class="metric"><span>表</span><b>{{ erStats.tableCount }}</b></div>
              <div class="metric"><span>外键</span><b>{{ erStats.relationshipCount }}</b></div>
              <div class="metric"><span>平均列数</span><b>{{ erStats.avgColumnsPerTable.toFixed(1) }}</b></div>
              <div class="metric"><span>孤立表</span><b>{{ erStats.orphanTables.length }}</b></div>
            </div>
            <div v-if="erStats.maxColumnsTable" class="hint">
              最宽表: {{ erStats.maxColumnsTable.name }} ({{ erStats.maxColumnsTable.count }} 列)
            </div>
            <div v-if="erStats.hubTables.length" class="block">
              <h4>关联枢纽 TOP</h4>
              <div v-for="hub in erStats.hubTables.slice(0, 5)" :key="hub.name" class="hub-row">
                <span>{{ hub.name }}</span>
                <span class="hub-count">{{ hub.total }} 关联</span>
              </div>
            </div>
            <div v-if="erStats.insights.length" class="block">
              <h4>Schema 洞察</h4>
              <div v-for="(item, i) in erStats.insights" :key="i" class="insight" :class="item.level">
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </aside>
          <div class="canvas-wrap scrollbar">
            <svg :width="canvasWidth * zoom" :height="canvasHeight * zoom" :viewBox="`0 0 ${canvasWidth} ${canvasHeight}`">
              <defs>
                <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                  <path d="M0,0 L6,3 L0,6 Z" fill="#3370ff" />
                </marker>
              </defs>
              <line
                v-for="(rel, i) in layoutRelations"
                :key="'rel-' + i"
                :x1="rel.x1" :y1="rel.y1" :x2="rel.x2" :y2="rel.y2"
                stroke="#3370ff" stroke-width="1.5" marker-end="url(#arrow)"
              />
              <g v-for="node in layoutNodes" :key="node.name" :transform="`translate(${node.x}, ${node.y})`">
                <rect :width="nodeWidth" :height="node.height" rx="4" fill="#fff" stroke="#3370ff" stroke-width="1.5" />
                <rect :width="nodeWidth" height="24" rx="4" fill="#e8f0fe" />
                <text x="8" y="16" font-size="12" font-weight="600" fill="#1f2329">{{ node.name }}</text>
                <text
                  v-for="(col, ci) in node.columns"
                  :key="col.name"
                  x="8"
                  :y="36 + ci * 16"
                  font-size="10"
                  :fill="col.key === 'PRI' ? '#3370ff' : '#646a73'"
                >
                  {{ col.key === 'PRI' ? '🔑 ' : '' }}{{ col.name }} : {{ col.type }}
                </text>
              </g>
            </svg>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { ErDiagramData } from '@/types/features'
import { analyzeErDiagram } from '@/utils/erDiagramAnalytics'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const loading = ref(false)
const error = ref('')
const data = ref<ErDiagramData | null>(null)
const zoom = ref(1)
const showStats = ref(true)
const nodeWidth = 180
const colHeight = 16
const headerHeight = 24
const gapX = 40
const gapY = 30

watch(() => props.modelValue, v => { if (v) load() })

const erStats = computed(() => analyzeErDiagram(data.value))

const layoutNodes = computed(() => {
  if (!data.value) return []
  const cols = 3
  return data.value.tables.map((table, index) => {
    const col = index % cols
    const row = Math.floor(index / cols)
    const height = headerHeight + table.columns.length * colHeight + 8
    return {
      name: table.name,
      columns: table.columns,
      x: 20 + col * (nodeWidth + gapX),
      y: 20 + row * (220 + gapY),
      height
    }
  })
})

const layoutRelations = computed(() => {
  if (!data.value) return []
  const nodeMap = Object.fromEntries(layoutNodes.value.map(n => [n.name, n]))
  return data.value.relationships.map(rel => {
    const from = nodeMap[rel.fromTable]
    const to = nodeMap[rel.toTable]
    if (!from || !to) return null
    return {
      x1: from.x + nodeWidth,
      y1: from.y + headerHeight / 2,
      x2: to.x,
      y2: to.y + headerHeight / 2
    }
  }).filter(Boolean) as Array<{ x1: number; y1: number; x2: number; y2: number }>
})

const canvasWidth = computed(() => {
  const maxX = Math.max(...layoutNodes.value.map(n => n.x + nodeWidth), 600)
  return maxX + 40
})

const canvasHeight = computed(() => {
  const maxY = Math.max(...layoutNodes.value.map(n => n.y + n.height), 400)
  return maxY + 40
})

function close() { emit('update:modelValue', false) }
function zoomIn() { zoom.value = Math.min(2, zoom.value + 0.1) }
function zoomOut() { zoom.value = Math.max(0.5, zoom.value - 0.1) }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await api.getErDiagram(props.connectionId, props.database)
    data.value = {
      database: result.database,
      tables: result.tables.map(t => ({ name: t.name, columns: t.columns.map(c => ({ name: c.name, type: c.type, key: c.key })) })),
      relationships: result.relationships
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.er-dialog { width: 960px; max-width: calc(100vw - 24px); height: 80vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.header-actions { display: flex; align-items: center; gap: 6px; }
.header-actions .active { color: var(--accent); background: var(--accent-light); }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { flex: 1; min-height: 0; padding: 12px; }
.body-layout { display: flex; gap: 10px; height: 100%; min-height: 0; }
.stats-panel { width: 220px; flex-shrink: 0; overflow: auto; border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; background: var(--bg-secondary); }
.canvas-wrap { flex: 1; min-width: 0; overflow: auto; background: #fafbfc; border: 1px solid var(--border); border-radius: var(--radius); }
.state { padding: 40px; text-align: center; color: var(--text-muted); }
.state.error { color: var(--danger); }
.btn-sm { padding: 3px 8px; font-size: 11px; }
.metric-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-bottom: 8px; }
.metric { border: 1px solid var(--border); border-radius: var(--radius); padding: 6px; font-size: 11px; background: var(--bg-primary); }
.metric span { display: block; color: var(--text-muted); }
.hint { font-size: 11px; color: var(--text-muted); margin-bottom: 8px; }
.block h4 { margin: 0 0 6px; font-size: 11px; color: var(--text-secondary); }
.hub-row { display: flex; justify-content: space-between; font-size: 11px; margin-bottom: 4px; }
.hub-count { color: var(--accent); }
.insight { font-size: 10px; padding: 6px; margin-bottom: 4px; border-left: 3px solid var(--border); background: var(--bg-primary); border-radius: 0 var(--radius) var(--radius) 0; }
.insight p { margin: 2px 0 0; color: var(--text-secondary); }
.insight.warn { border-left-color: var(--warning); }
.insight.info { border-left-color: var(--accent); }
</style>
