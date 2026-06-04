<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog large">
      <div class="dialog-header">
        <h2>可视化建表设计器</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <div class="dialog-body">
        <div class="designer-layout">
          <div class="designer-main">
            <div class="form-row">
              <label>表名<input v-model="tableName" placeholder="users" /></label>
              <label>引擎<select v-model="engine"><option>InnoDB</option><option>MyISAM</option></select></label>
              <label>字符集<input v-model="charset" /></label>
            </div>
            <div class="columns-toolbar">
              <span>字段设计</span>
              <button class="btn btn-secondary btn-sm" @click="addColumn">+ 添加字段</button>
            </div>
            <div class="columns-table scrollbar">
              <table>
                <thead>
                  <tr>
                    <th>字段名</th><th>类型</th><th>长度</th><th>可空</th><th>主键</th><th>自增</th><th>默认值</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(col, i) in columns" :key="i">
                    <td><input v-model="col.name" /></td>
                    <td>
                      <select v-model="col.type">
                        <option v-for="t in dataTypes" :key="t" :value="t">{{ t }}</option>
                      </select>
                    </td>
                    <td><input v-model="col.length" placeholder="255" /></td>
                    <td><input v-model="col.nullable" type="checkbox" /></td>
                    <td><input v-model="col.primaryKey" type="checkbox" @change="onPrimaryChange(col)" /></td>
                    <td><input v-model="col.autoIncrement" type="checkbox" /></td>
                    <td><input v-model="col.defaultValue" placeholder="可选" /></td>
                    <td><button class="icon-btn danger" @click="columns.splice(i, 1)">×</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <label>DDL 预览<textarea :value="generatedDdl" rows="6" readonly spellcheck="false" /></label>
          </div>
          <aside class="designer-side">
            <div class="score-card">
              <div class="score" :class="scoreClass">{{ designerStats.healthScore }}</div>
              <div>
                <strong>设计健康度</strong>
                <p>{{ designerStats.columnCount }} 字段 · {{ designerStats.primaryKeyCount }} 主键</p>
              </div>
            </div>
            <div v-for="(item, i) in designerStats.insights" :key="i" class="insight" :class="item.level">
              <strong>{{ item.title }}</strong>
              <p>{{ item.detail }}</p>
            </div>
          </aside>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" @click="copyDdl">复制 DDL</button>
        <button class="btn btn-secondary" @click="close">取消</button>
        <button class="btn btn-primary" :disabled="submitting" @click="submit">创建表</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import type { DesignerColumn } from '@/types/features'
import { analyzeDesigner } from '@/utils/designerAnalytics'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string; editTable?: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [] }>()

const tableName = ref('new_table')
const engine = ref('InnoDB')
const charset = ref('utf8mb4')
const submitting = ref(false)
const error = ref('')
const dataTypes = ['INT', 'BIGINT', 'VARCHAR', 'TEXT', 'DATETIME', 'DATE', 'DECIMAL', 'BOOLEAN', 'JSON']

const columns = ref<DesignerColumn[]>([
  { name: 'id', type: 'BIGINT', length: '', nullable: false, primaryKey: true, autoIncrement: true, defaultValue: '' },
  { name: 'name', type: 'VARCHAR', length: '255', nullable: false, primaryKey: false, autoIncrement: false, defaultValue: '' }
])

watch(() => props.modelValue, v => {
  if (v) {
    error.value = ''
    if (props.editTable) tableName.value = props.editTable
  }
})

const generatedDdl = computed(() => buildDdl())
const designerStats = computed(() => analyzeDesigner(tableName.value, columns.value))
const scoreClass = computed(() => {
  const s = designerStats.value.healthScore
  if (s >= 85) return 'good'
  if (s >= 70) return 'ok'
  return 'warn'
})

function buildDdl() {
  const defs = columns.value.filter(c => c.name.trim()).map(col => {
    let type = col.type
    if (col.length && ['VARCHAR', 'CHAR', 'DECIMAL', 'INT', 'BIGINT'].includes(col.type)) {
      type += `(${col.length})`
    }
    let def = `\`${col.name}\` ${type}`
    if (!col.nullable) def += ' NOT NULL'
    if (col.autoIncrement) def += ' AUTO_INCREMENT'
    if (col.defaultValue) def += ` DEFAULT '${col.defaultValue.replace(/'/g, "''")}'`
    return def
  })
  const pk = columns.value.filter(c => c.primaryKey).map(c => `\`${c.name}\``)
  if (pk.length) defs.push(`PRIMARY KEY (${pk.join(', ')})`)
  return `CREATE TABLE \`${tableName.value || 'new_table'}\` (\n  ${defs.join(',\n  ')}\n) ENGINE=${engine.value} DEFAULT CHARSET=${charset.value};`
}

function addColumn() {
  columns.value.push({ name: '', type: 'VARCHAR', length: '255', nullable: true, primaryKey: false, autoIncrement: false, defaultValue: '' })
}

function onPrimaryChange(col: DesignerColumn) {
  if (col.primaryKey) col.nullable = false
}

function close() { emit('update:modelValue', false) }

function copyDdl() {
  navigator.clipboard.writeText(generatedDdl.value)
}

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    await api.createTable(props.connectionId, props.database, generatedDdl.value)
    emit('created')
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.large { width: 900px; max-width: calc(100vw - 24px); max-height: 90vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { padding: 16px; overflow: auto; display: flex; flex-direction: column; gap: 12px; }
.designer-layout { display: grid; grid-template-columns: 1fr 220px; gap: 12px; }
.designer-side { border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; background: var(--bg-secondary); display: flex; flex-direction: column; gap: 8px; }
.score-card { display: flex; align-items: center; gap: 10px; padding: 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-primary); }
.score { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; border: 3px solid var(--accent); }
.score.good { border-color: var(--success); color: #1a7f37; }
.score.ok { border-color: var(--accent); color: var(--accent); }
.score.warn { border-color: var(--warning); color: #b8860b; }
.score-card p { margin: 4px 0 0; font-size: 10px; color: var(--text-muted); }
.insight { font-size: 10px; padding: 6px 8px; border-left: 3px solid var(--border); background: var(--bg-primary); border-radius: 0 var(--radius) var(--radius) 0; }
.insight p { margin: 2px 0 0; color: var(--text-secondary); }
.insight.good { border-left-color: var(--success); }
.insight.warn { border-left-color: var(--warning); }
.insight.info { border-left-color: var(--accent); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border); background: var(--bg-secondary); }
.form-row { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 10px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.columns-toolbar { display: flex; justify-content: space-between; align-items: center; font-size: 12px; font-weight: 600; }
.columns-table { max-height: 220px; overflow: auto; border: 1px solid var(--border); border-radius: var(--radius); }
.columns-table table { width: 100%; border-collapse: collapse; font-size: 12px; }
.columns-table th, .columns-table td { border: 1px solid var(--border); padding: 4px 6px; }
.columns-table input, .columns-table select { width: 100%; font-size: 12px; padding: 2px 4px; }
textarea { font-family: monospace; font-size: 11px; resize: vertical; }
.error { margin: 0; color: var(--danger); font-size: 12px; }
.btn-sm { padding: 3px 8px; font-size: 11px; }
.icon-btn { border: none; background: transparent; color: var(--text-muted); }
.icon-btn.danger:hover { color: var(--danger); }
</style>
