<template>
  <div class="result-grid">
    <div v-if="error" class="state error">{{ error }}</div>
    <div v-else-if="!result" class="state">运行 SQL 后在这里查看结果</div>
    <div v-else-if="result.type !== 'SELECT'" class="state success">{{ result.message }}</div>
    <template v-else>
      <div class="grid-toolbar">
        <input v-model="filterText" class="filter-input" placeholder="筛选结果..." />
        <select v-model.number="pageSize" class="page-size">
          <option :value="50">50 行/页</option>
          <option :value="100">100 行/页</option>
          <option :value="200">200 行/页</option>
          <option :value="500">500 行/页</option>
        </select>
        <span class="meta">{{ filteredRows.length }} / {{ result.rows?.length ?? 0 }} 行</span>
        <span class="toolbar-spacer" />
        <span v-if="selectionSummary" class="selection-hint">{{ selectionSummary }}</span>
        <button
          class="btn btn-secondary btn-sm"
          :disabled="!hasSelection"
          title="复制选中区域 (Ctrl+C)"
          @click="copySelection"
        >
          复制
        </button>
        <span v-if="editable && editContext" class="edit-badge">可编辑 · {{ editContext.table }}</span>
        <span v-else-if="result.editableReason" class="readonly-hint">{{ result.editableReason }}</span>
        <button v-if="editable" class="btn btn-secondary btn-sm" @click="addNewRow">+ 新增行</button>
        <button v-if="editable && editingRow !== null" class="btn btn-primary btn-sm" @click="saveRow">保存</button>
        <button v-if="editable && editingRow !== null" class="btn btn-secondary btn-sm" @click="cancelEdit">取消</button>
      </div>

      <div
        ref="tableWrapRef"
        class="table-wrap scrollbar"
        :class="{ selecting: isSelecting }"
        tabindex="0"
        @keydown="onTableKeyDown"
      >
        <table>
          <thead>
            <tr>
              <th v-if="editable" class="action-col">#</th>
              <th
                v-for="column in result.columns"
                :key="column"
                class="sortable"
                :class="{ sorted: sortColumn === column }"
                @click="toggleSort(column)"
              >
                {{ column }}
                <span class="sort-icon">{{ sortIcon(column) }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in pagedRows"
              :key="rowKey(row, index)"
              :class="{ editing: editingRow === absoluteIndex(index) }"
              @dblclick="editable && startEdit(absoluteIndex(index))"
            >
              <td v-if="editable" class="action-col">
                <button class="icon-btn" title="编辑" @mousedown.stop @click="startEdit(absoluteIndex(index))">✎</button>
                <button class="icon-btn danger" title="删除" @mousedown.stop @click="deleteRow(row)">×</button>
              </td>
              <td
                v-for="(column, colIndex) in result.columns"
                :key="column"
                :class="{ selected: isCellSelected(absoluteIndex(index), colIndex) }"
                @mousedown="onCellMouseDown(absoluteIndex(index), colIndex, $event)"
                @mouseenter="onCellMouseEnter(absoluteIndex(index), colIndex)"
              >
                <input
                  v-if="editable && editingRow === absoluteIndex(index)"
                  v-model="editBuffer[column]"
                  class="cell-input"
                  @mousedown.stop
                  @click.stop
                />
                <span v-else :class="{ null: row[column] == null }">{{ formatCell(row[column]) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="page--">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn btn-ghost btn-sm" :disabled="page >= totalPages" @click="page++">下一页</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '@/api'
import type { SqlExecuteResponse } from '@/types'
import type { TableEditContext } from '@/types/features'

const props = defineProps<{
  result: SqlExecuteResponse | null
  error: string
  editable?: boolean
  editContext?: TableEditContext | null
}>()

const emit = defineEmits<{
  refresh: []
  notify: [message: string, type?: 'success' | 'error' | 'info']
  'selection-change': [payload: { rows: Record<string, unknown>[]; columns: string[] } | null]
}>()

interface CellRange {
  startRow: number
  startCol: number
  endRow: number
  endCol: number
}

const filterText = ref('')
const sortColumn = ref('')
const sortDir = ref<'asc' | 'desc'>('asc')
const page = ref(1)
const pageSize = ref(100)
const editingRow = ref<number | null>(null)
const editBuffer = ref<Record<string, string>>({})
const editPrimaryKey = ref<Record<string, unknown>>({})
const isNewRow = ref(false)
const tableWrapRef = ref<HTMLElement | null>(null)
const cellSelection = ref<CellRange | null>(null)
const isSelecting = ref(false)

watch(() => props.result, () => {
  filterText.value = ''
  sortColumn.value = ''
  page.value = 1
  clearSelection()
  cancelEdit()
})

watch([filterText, sortColumn, sortDir, pageSize, page], () => {
  clearSelection()
})

function emitSelectionChange() {
  if (!cellSelection.value || !props.result?.columns?.length) {
    emit('selection-change', null)
    return
  }
  const { minRow, maxRow, minCol, maxCol } = normalizeRange(cellSelection.value)
  const columns = props.result.columns.slice(minCol, maxCol + 1)
  const rows: Record<string, unknown>[] = []
  for (let rowIndex = minRow; rowIndex <= maxRow; rowIndex++) {
    const row = filteredRows.value[rowIndex]
    if (!row) continue
    const subset: Record<string, unknown> = {}
    for (const col of columns) subset[col] = row[col]
    rows.push(subset)
  }
  emit('selection-change', rows.length ? { rows, columns } : null)
}

watch(cellSelection, () => emitSelectionChange(), { deep: true })

function clearSelection() {
  cellSelection.value = null
  isSelecting.value = false
  emit('selection-change', null)
}

function normalizeRange(range: CellRange) {
  return {
    minRow: Math.min(range.startRow, range.endRow),
    maxRow: Math.max(range.startRow, range.endRow),
    minCol: Math.min(range.startCol, range.endCol),
    maxCol: Math.max(range.startCol, range.endCol)
  }
}

const hasSelection = computed(() => cellSelection.value !== null)

const selectionSummary = computed(() => {
  if (!cellSelection.value) return ''
  const { minRow, maxRow, minCol, maxCol } = normalizeRange(cellSelection.value)
  const rows = maxRow - minRow + 1
  const cols = maxCol - minCol + 1
  return `已选 ${rows}×${cols}`
})

const filteredRows = computed(() => {
  const rows = props.result?.rows ?? []
  const keyword = filterText.value.trim().toLowerCase()
  let list = keyword
    ? rows.filter(row => Object.values(row).some(v => String(v ?? '').toLowerCase().includes(keyword)))
    : [...rows]

  if (sortColumn.value) {
    list.sort((a, b) => {
      const av = a[sortColumn.value]
      const bv = b[sortColumn.value]
      if (av == null && bv == null) return 0
      if (av == null) return sortDir.value === 'asc' ? -1 : 1
      if (bv == null) return sortDir.value === 'asc' ? 1 : -1
      const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true })
      return sortDir.value === 'asc' ? cmp : -cmp
    })
  }
  return list
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pageSize.value)))
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

function absoluteIndex(pageIndex: number) {
  return (page.value - 1) * pageSize.value + pageIndex
}

function toggleSort(column: string) {
  if (sortColumn.value === column) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortColumn.value = column
    sortDir.value = 'asc'
  }
}

function sortIcon(column: string) {
  if (sortColumn.value !== column) return '↕'
  return sortDir.value === 'asc' ? '↑' : '↓'
}

function formatCell(value: unknown) {
  if (value == null) return 'NULL'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatCellForCopy(value: unknown) {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function isCellSelected(rowIndex: number, colIndex: number) {
  if (!cellSelection.value) return false
  const { minRow, maxRow, minCol, maxCol } = normalizeRange(cellSelection.value)
  return rowIndex >= minRow && rowIndex <= maxRow && colIndex >= minCol && colIndex <= maxCol
}

function onCellMouseDown(rowIndex: number, colIndex: number, event: MouseEvent) {
  if (editingRow.value !== null) return
  if (event.button !== 0) return

  tableWrapRef.value?.focus()

  if (event.shiftKey && cellSelection.value) {
    cellSelection.value = {
      ...cellSelection.value,
      endRow: rowIndex,
      endCol: colIndex
    }
    return
  }

  cellSelection.value = {
    startRow: rowIndex,
    startCol: colIndex,
    endRow: rowIndex,
    endCol: colIndex
  }
  isSelecting.value = true
}

function onCellMouseEnter(rowIndex: number, colIndex: number) {
  if (!isSelecting.value || !cellSelection.value) return
  cellSelection.value = {
    ...cellSelection.value,
    endRow: rowIndex,
    endCol: colIndex
  }
}

function stopSelecting() {
  isSelecting.value = false
}

async function copySelection() {
  if (!cellSelection.value || !props.result?.columns?.length) return

  const { minRow, maxRow, minCol, maxCol } = normalizeRange(cellSelection.value)
  const lines: string[] = []

  for (let rowIndex = minRow; rowIndex <= maxRow; rowIndex++) {
    const row = filteredRows.value[rowIndex]
    if (!row) continue
    const cells: string[] = []
    for (let colIndex = minCol; colIndex <= maxCol; colIndex++) {
      const column = props.result.columns[colIndex]
      cells.push(formatCellForCopy(row[column]))
    }
    lines.push(cells.join('\t'))
  }

  if (!lines.length) return

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    emit('notify', `已复制 ${lines.length} 行`, 'success')
  } catch {
    emit('notify', '复制失败，请检查浏览器权限', 'error')
  }
}

function onTableKeyDown(event: KeyboardEvent) {
  if (!(event.metaKey || event.ctrlKey)) return

  if (event.key.toLowerCase() === 'c') {
    if (!hasSelection.value) return
    event.preventDefault()
    void copySelection()
    return
  }

  if (event.key.toLowerCase() === 'a') {
    if (!props.result?.columns?.length || !filteredRows.value.length) return
    event.preventDefault()
    cellSelection.value = {
      startRow: 0,
      startCol: 0,
      endRow: filteredRows.value.length - 1,
      endCol: props.result.columns.length - 1
    }
  }
}

onMounted(() => {
  window.addEventListener('mouseup', stopSelecting)
})

onBeforeUnmount(() => {
  window.removeEventListener('mouseup', stopSelecting)
})

function rowKey(row: Record<string, unknown>, index: number) {
  const ctx = props.editContext
  if (ctx?.primaryKeys.length) {
    return ctx.primaryKeys.map(k => row[k]).join('|')
  }
  return String(index)
}

function cancelEdit() {
  editingRow.value = null
  isNewRow.value = false
  editBuffer.value = {}
  editPrimaryKey.value = {}
}

function buildRowIdentity(row: Record<string, unknown>) {
  const identity: Record<string, unknown> = {}
  const pkColumns = props.editContext?.primaryKeys ?? []
  if (pkColumns.length) {
    for (const pk of pkColumns) identity[pk] = row[pk]
    return identity
  }
  for (const col of props.result?.columns ?? []) {
    identity[col] = row[col]
  }
  return identity
}

function startEdit(index: number) {
  clearSelection()
  const row = filteredRows.value[index]
  if (!row) return
  editingRow.value = index
  isNewRow.value = false
  editBuffer.value = {}
  editPrimaryKey.value = buildRowIdentity(row)
  for (const col of props.result?.columns ?? []) {
    editBuffer.value[col] = row[col] == null ? '' : String(row[col])
  }
}

function addNewRow() {
  clearSelection()
  editingRow.value = filteredRows.value.length
  isNewRow.value = true
  editBuffer.value = {}
  editPrimaryKey.value = {}
  for (const col of props.result?.columns ?? []) {
    editBuffer.value[col] = ''
  }
}

async function saveRow() {
  const ctx = props.editContext
  if (!ctx) return
  const data: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(editBuffer.value)) {
    data[key] = value === '' ? null : value
  }
  try {
    if (isNewRow.value) {
      await api.insertRow(ctx.connectionId, ctx.database, ctx.table, { data })
      emit('notify', '行已插入', 'success')
    } else {
      await api.updateRow(ctx.connectionId, ctx.database, ctx.table, { data, primaryKey: editPrimaryKey.value })
      emit('notify', '行已更新', 'success')
    }
    cancelEdit()
    emit('refresh')
  } catch (e) {
    emit('notify', e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function deleteRow(row: Record<string, unknown>) {
  const ctx = props.editContext
  if (!ctx) return
  const primaryKey = buildRowIdentity(row)
  if (!Object.keys(primaryKey).length) {
    emit('notify', '无法删除：缺少行标识', 'error')
    return
  }
  if (!window.confirm('确认删除该行？')) return
  try {
    await api.deleteRow(ctx.connectionId, ctx.database, ctx.table, { data: {}, primaryKey })
    emit('notify', '行已删除', 'success')
    emit('refresh')
  } catch (e) {
    emit('notify', e instanceof Error ? e.message : '删除失败', 'error')
  }
}
</script>

<style scoped>
.result-grid { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.state { color: var(--text-muted); padding: 32px; text-align: center; font-size: 12px; }
.state.success { color: #1a7f37; }
.state.error { color: var(--danger); }
.grid-toolbar { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.filter-input { flex: 1; max-width: 240px; font-size: 12px; padding: 4px 8px; }
.page-size { font-size: 12px; padding: 4px 6px; }
.toolbar-spacer { flex: 1; }
.meta { font-size: 11px; color: var(--text-muted); }
.selection-hint { font-size: 11px; color: var(--accent); }
.btn-sm { padding: 3px 8px; font-size: 11px; }
.table-wrap { flex: 1; min-height: 0; overflow: auto; outline: none; }
.table-wrap.selecting { user-select: none; cursor: cell; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { border: 1px solid var(--border); padding: 5px 8px; text-align: left; vertical-align: middle; white-space: nowrap; }
td { cursor: cell; }
td.selected { background: rgba(51, 112, 255, 0.16); }
tbody tr:hover td.selected { background: rgba(51, 112, 255, 0.24); }
th { position: sticky; top: 0; background: var(--bg-tertiary); z-index: 1; font-weight: 600; color: var(--text-secondary); user-select: none; }
th.sortable { cursor: pointer; }
th.sortable:hover { background: var(--bg-hover); }
th.sorted { color: var(--accent); }
.sort-icon { margin-left: 4px; font-size: 10px; color: var(--text-muted); }
.action-col { width: 56px; text-align: center; }
.icon-btn { border: none; background: transparent; color: var(--text-muted); padding: 0 3px; font-size: 12px; }
.icon-btn.danger:hover { color: var(--danger); }
tbody tr:hover { background: var(--accent-light); }
tbody tr.editing { background: #fff8e6; }
.edit-badge {
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-light);
  border: 1px solid #b3ccff;
  padding: 2px 8px;
  border-radius: 999px;
}
.readonly-hint {
  font-size: 11px;
  color: var(--text-muted);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.null { color: var(--text-muted); font-style: italic; }
.cell-input { width: 100%; min-width: 80px; font-size: 12px; padding: 2px 4px; border: 1px solid var(--accent); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 6px; border-top: 1px solid var(--border); font-size: 12px; flex-shrink: 0; }
</style>
