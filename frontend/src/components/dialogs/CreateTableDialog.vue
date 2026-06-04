<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header">
        <h2>新建表</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <form class="dialog-body" @submit.prevent="submit">
        <div class="layout">
          <label class="ddl-label">
            DDL 语句
            <textarea v-model="ddl" rows="12" spellcheck="false" placeholder="CREATE TABLE ..." />
          </label>
          <aside v-if="ddl.trim()" class="side-panel">
            <div class="score-row">
              <div class="score" :class="scoreClass">{{ ddlStats.healthScore }}</div>
              <div>
                <strong>DDL 健康度</strong>
                <p v-if="ddlStats.tableName">表: {{ ddlStats.tableName }}</p>
              </div>
            </div>
            <div class="mini-metrics">
              <span>字段约 {{ ddlStats.columnCount }}</span>
              <span>{{ ddlStats.hasPrimaryKey ? '有主键' : '无主键' }}</span>
            </div>
            <div v-for="(item, i) in ddlStats.insights" :key="i" class="insight" :class="item.level">
              <strong>{{ item.title }}</strong>
              <p>{{ item.detail }}</p>
            </div>
          </aside>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" @click="fillTemplate">使用模板</button>
          <button type="button" class="btn btn-secondary" @click="close">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">创建</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { analyzeDdl } from '@/utils/designerAnalytics'

const props = defineProps<{ modelValue: boolean; connectionId: string; database: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [] }>()

const ddl = ref('')
const submitting = ref(false)
const error = ref('')

const ddlStats = computed(() => analyzeDdl(ddl.value))
const scoreClass = computed(() => {
  const s = ddlStats.value.healthScore
  if (s >= 85) return 'good'
  if (s >= 70) return 'ok'
  return 'warn'
})

watch(() => props.modelValue, v => { if (v) { error.value = '' } })

function close() { emit('update:modelValue', false) }

function fillTemplate() {
  ddl.value = `CREATE TABLE \`new_table\` (
  \`id\` BIGINT NOT NULL AUTO_INCREMENT,
  \`name\` VARCHAR(255) NOT NULL,
  \`created_at\` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (\`id\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`
}

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    await api.createTable(props.connectionId, props.database, ddl.value)
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
.dialog.wide { width: 720px; max-width: calc(100vw - 32px); background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.layout { display: grid; grid-template-columns: 1fr 200px; gap: 12px; }
.ddl-label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
textarea { font-family: 'JetBrains Mono', monospace; font-size: 12px; resize: vertical; }
.side-panel { border: 1px solid var(--border); border-radius: var(--radius); padding: 10px; background: var(--bg-secondary); font-size: 11px; }
.score-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.score { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; border: 3px solid var(--accent); font-size: 13px; }
.score.good { border-color: var(--success); color: #1a7f37; }
.score.ok { border-color: var(--accent); }
.score.warn { border-color: var(--warning); color: #b8860b; }
.mini-metrics { display: flex; flex-direction: column; gap: 2px; color: var(--text-muted); margin-bottom: 8px; }
.insight { padding: 6px 8px; margin-bottom: 4px; border-left: 3px solid var(--border); background: var(--bg-primary); border-radius: 0 var(--radius) var(--radius) 0; }
.insight p { margin: 2px 0 0; color: var(--text-secondary); }
.insight.good { border-left-color: var(--success); }
.insight.warn { border-left-color: var(--warning); }
.insight.info { border-left-color: var(--accent); }
.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; }
.error { margin: 0; color: var(--danger); font-size: 12px; }
</style>
