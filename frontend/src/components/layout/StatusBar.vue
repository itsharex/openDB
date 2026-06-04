<template>
  <footer class="status-bar">
    <div class="status-left">
      <span class="status-item">{{ connectionStatus }}</span>
      <span v-if="hasConnection" class="status-sep">|</span>
      <button
        v-if="hasConnection && database"
        type="button"
        class="status-item status-db"
        title="当前操作的数据库"
      >
        数据库: {{ database }}
      </button>
      <button
        v-else-if="hasConnection"
        type="button"
        class="status-item status-db-warning"
        title="点击选择数据库"
        @click="$emit('select-database')"
      >
        未选择数据库 — 点击选择
      </button>
    </div>
    <div class="status-center">
      <span v-if="aiStatus" class="status-item running">● AI {{ aiStatus }}</span>
      <span v-else-if="executing" class="status-item running">● 正在执行...</span>
      <span v-else-if="resultMessage" class="status-item">{{ resultMessage }}</span>
      <span v-else class="status-item muted">就绪</span>
    </div>
    <div class="status-right">
      <span class="status-item">openDB v0.1.0</span>
      <span class="status-sep">|</span>
      <span class="status-item">{{ databaseTypeLabel }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
defineProps<{
  connectionStatus: string
  hasConnection: boolean
  database: string
  databaseTypeLabel?: string
  executing: boolean
  aiStatus?: string
  resultMessage: string
}>()

defineEmits<{ 'select-database': [] }>()
</script>

<style scoped>
.status-bar {
  height: var(--statusbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.status-left,
.status-center,
.status-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-center {
  flex: 1;
  justify-content: center;
}

.status-item.running {
  color: var(--accent);
}

.status-item.muted {
  color: var(--text-muted);
}

.status-db {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--accent);
  font: inherit;
}

.status-db-warning {
  border: 1px solid #ffe58f;
  background: #fffbe6;
  color: #ad6800;
  padding: 1px 8px;
  border-radius: 999px;
  font: inherit;
  cursor: pointer;
}

.status-db-warning:hover {
  border-color: #ffc53d;
  background: #fff1b8;
}

.status-sep {
  color: var(--border-strong);
}
</style>
