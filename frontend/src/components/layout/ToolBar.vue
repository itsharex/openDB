<template>
  <div class="toolbar">
    <template v-for="item in toolbarItems" :key="item.id">
      <div v-if="item.action === 'separator'" class="toolbar-sep" />
      <button
        v-else
        class="toolbar-btn"
        :class="{ primary: item.primary, active: item.action === 'toggleAiPanel' && aiPanelVisible }"
        :title="item.label"
        :disabled="isDisabled(item)"
        @click="$emit('action', item.action)"
      >
        <span class="toolbar-icon">{{ item.icon }}</span>
        <span class="toolbar-label">{{ item.label }}</span>
      </button>
    </template>

    <div class="toolbar-spacer" />

    <DatabaseSelector
      v-if="hasConnection"
      ref="dbSelectorRef"
      :model-value="database"
      :databases="databases"
      :loading="databasesLoading"
      placeholder="选择数据库"
      @update:model-value="$emit('select-database', $event)"
      @open="$emit('refresh-databases')"
    />

    <div class="toolbar-info">
      <span v-if="connectionName" class="conn-badge">
        <span class="conn-dot" />
        {{ connectionName }}
      </span>
      <span class="ai-badge" :class="{ online: aiOnline }" :title="aiLabel || 'AI 状态'">
        AI {{ aiOnline ? (aiLabel || '已启用') : '未启用' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import DatabaseSelector from '@/components/DatabaseSelector.vue'
import { toolbarItems, type ToolbarItem } from '@/config/menus'

defineProps<{
  connectionName: string
  database: string
  databases: string[]
  databasesLoading?: boolean
  aiOnline: boolean
  aiLabel?: string
  aiPanelVisible: boolean
  hasConnection: boolean
}>()

defineEmits<{
  action: [action: string]
  'select-database': [database: string]
  'refresh-databases': []
}>()

const dbSelectorRef = ref<InstanceType<typeof DatabaseSelector> | null>(null)

function isDisabled(item: ToolbarItem) {
  if (item.disabled) return true
  if (['runSql', 'explainSql', 'formatSql'].includes(item.action)) {
    return false
  }
  return false
}

defineExpose({
  openDatabaseSelector: () => dbSelectorRef.value?.open(),
  pulseDatabaseSelector: () => dbSelectorRef.value?.pulse()
})
</script>

<style scoped>
.toolbar {
  height: var(--toolbar-height);
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 8px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  white-space: nowrap;
}

.toolbar-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: var(--border);
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.toolbar-btn.primary {
  color: var(--accent);
}

.toolbar-btn.primary:hover:not(:disabled) {
  background: var(--accent-light);
  border-color: #b3ccff;
}

.toolbar-btn.active {
  background: var(--accent-light);
  border-color: #b3ccff;
  color: var(--accent);
}

.toolbar-icon {
  font-size: 13px;
  line-height: 1;
}

.toolbar-label {
  font-size: 12px;
}

.toolbar-sep {
  width: 1px;
  height: 20px;
  background: var(--border);
  margin: 0 4px;
  flex-shrink: 0;
}

.toolbar-spacer {
  flex: 1;
}

.toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.conn-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  padding: 2px 8px;
  border-radius: 999px;
}

.conn-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
}

.db-badge {
  color: var(--accent);
  background: var(--accent-light);
  border: 1px solid #b3ccff;
  padding: 2px 8px;
  border-radius: 999px;
}

.ai-badge {
  color: var(--text-muted);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  padding: 2px 8px;
  border-radius: 999px;
}

.ai-badge.online {
  color: #1a7f37;
  background: var(--success-bg);
  border-color: #a8e6b4;
}
</style>
