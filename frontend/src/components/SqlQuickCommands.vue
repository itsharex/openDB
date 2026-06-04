<template>
  <div class="quick-commands">
    <div class="quick-groups scrollbar">
      <div v-for="group in sqlQuickCommandGroups" :key="group.id" class="command-group">
        <span class="group-label">{{ group.label }}</span>
        <button
          v-for="cmd in group.commands"
          :key="cmd.id"
          type="button"
          class="cmd-chip"
          :title="chipTitle(cmd)"
          @click="onClick($event, cmd)"
        >
          {{ cmd.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  resolveQuickSql,
  sqlQuickCommandGroups,
  type SqlQuickCommand
} from '@/config/sqlQuickCommands'

const props = defineProps<{
  database?: string
  table?: string
  currentSql?: string
}>()

const emit = defineEmits<{
  apply: [payload: { sql: string; run: boolean }]
}>()

function chipTitle(cmd: SqlQuickCommand) {
  const hints: string[] = [cmd.title, '点击插入编辑器', 'Shift+点击直接运行']
  if (cmd.requiresTable && !props.table) hints.unshift('未选表时将使用占位符 your_table')
  if (cmd.requiresDatabase && !props.database) hints.unshift('未选库时部分命令可能失败')
  return hints.join(' · ')
}

function buildSql(cmd: SqlQuickCommand) {
  if (cmd.id === 'explain' && props.currentSql?.trim()) {
    const trimmed = props.currentSql.trim().replace(/;+\s*$/, '')
    if (!trimmed.toUpperCase().startsWith('EXPLAIN')) {
      return `EXPLAIN ${trimmed};`
    }
    return trimmed.endsWith(';') ? trimmed : `${trimmed};`
  }
  return resolveQuickSql(cmd.sql, { database: props.database, table: props.table })
}

function onClick(event: MouseEvent, cmd: SqlQuickCommand) {
  emit('apply', { sql: buildSql(cmd), run: event.shiftKey })
}
</script>

<style scoped>
.quick-commands {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
}

.quick-groups {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px;
  overflow-x: auto;
}

.command-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.group-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding-right: 2px;
}

.cmd-chip {
  border: 1px solid var(--border-strong);
  background: var(--bg-primary);
  color: var(--text-secondary);
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 11px;
  font-family: 'JetBrains Mono', 'SF Mono', Consolas, monospace;
  white-space: nowrap;
}

.cmd-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}
</style>
