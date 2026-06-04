<template>
  <div class="workspace-panel">
    <div v-if="tabs.length > 0" class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab"
        :class="{ active: tab.id === activeTabId }"
        @click="$emit('switch-tab', tab.id)"
      >
        {{ tab.title }}
        <span v-if="tabs.length > 1" class="tab-close" @click.stop="$emit('close-tab', tab.id)">×</span>
      </button>
      <button class="tab-add" title="新建查询" @click="$emit('new-tab')">＋</button>
    </div>

    <div ref="panelBodyRef" class="panel-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
export interface QueryTab {
  id: string
  title: string
  sql: string
}

defineProps<{
  tabs: QueryTab[]
  activeTabId: string
}>()

defineEmits<{
  'switch-tab': [id: string]
  'close-tab': [id: string]
  'new-tab': []
}>()

const panelBodyRef = ref<HTMLElement | null>(null)

defineExpose({ panelBodyRef })
</script>

<style scoped>
.workspace-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}

.tab-bar {
  height: var(--tab-height);
  display: flex;
  align-items: stretch;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
  flex-shrink: 0;
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  border: none;
  border-right: 1px solid var(--border);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.tab:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.tab.active {
  background: var(--bg-primary);
  color: var(--accent);
  border-bottom: 2px solid var(--accent);
  margin-bottom: -1px;
}

.tab-close {
  font-size: 14px;
  line-height: 1;
  color: var(--text-muted);
  border-radius: 2px;
  padding: 0 2px;
}

.tab-close:hover {
  background: var(--bg-hover);
  color: var(--danger);
}

.tab-add {
  width: 32px;
  border: none;
  border-right: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
}

.tab-add:hover {
  background: var(--bg-hover);
  color: var(--accent);
}

.panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
