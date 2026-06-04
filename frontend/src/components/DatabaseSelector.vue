<template>
  <div
    ref="rootRef"
    class="db-selector"
    :class="{ empty: !modelValue, open, pulse: pulsing, disabled: !enabled }"
  >
    <button
      type="button"
      class="db-trigger"
      :disabled="!enabled"
      :title="enabled ? '选择当前操作的数据库' : '请先连接数据库'"
      @click.stop="toggle"
    >
      <span class="db-icon" aria-hidden="true">◫</span>
      <span class="db-label">{{ modelValue || placeholder }}</span>
      <span class="chevron" aria-hidden="true">▾</span>
    </button>

    <div v-if="open" class="db-dropdown" @click.stop>
      <div class="db-dropdown-header">选择数据库</div>
      <div v-if="loading" class="db-empty">加载中...</div>
      <div v-else-if="databases.length === 0" class="db-empty">当前连接下暂无数据库</div>
      <button
        v-for="db in databases"
        :key="db"
        type="button"
        class="db-item"
        :class="{ active: db === modelValue }"
        @click="pick(db)"
      >
        <span class="item-icon">◫</span>
        <span class="item-name">{{ db }}</span>
        <span v-if="db === modelValue" class="item-check">✓</span>
      </button>
      <div class="db-footnote">也可在左侧展开连接后点击数据库名称</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

defineProps<{
  modelValue: string
  databases: string[]
  enabled?: boolean
  loading?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [string]; open: [] }>()

const open = ref(false)
const pulsing = ref(false)
const rootRef = ref<HTMLElement | null>(null)

function toggle() {
  if (!open.value) emit('open')
  open.value = !open.value
}

function pick(db: string) {
  emit('update:modelValue', db)
  open.value = false
}

function close() {
  open.value = false
}

function onDocumentClick(event: MouseEvent) {
  if (!open.value) return
  const target = event.target as Node
  if (rootRef.value && !rootRef.value.contains(target)) close()
}

function pulseHighlight() {
  pulsing.value = true
  window.setTimeout(() => { pulsing.value = false }, 2400)
}

defineExpose({ open: () => { emit('open'); open.value = true }, close, pulse: pulseHighlight })

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))
</script>

<style scoped>
.db-selector {
  position: relative;
  flex-shrink: 0;
}

.db-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 12px;
  max-width: 220px;
}

.db-selector.empty .db-trigger {
  border-color: #ffc53d;
  background: #fffbe6;
  color: #ad6800;
}

.db-selector.empty.pulse .db-trigger {
  animation: db-pulse 0.8s ease-in-out 3;
}

@keyframes db-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 197, 61, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(255, 197, 61, 0.25); }
}

.db-selector.open .db-trigger,
.db-trigger:hover:not(:disabled) {
  border-color: var(--accent);
  background: var(--accent-light);
  color: var(--accent);
}

.db-selector.disabled .db-trigger {
  opacity: 0.5;
  cursor: not-allowed;
}

.db-icon {
  font-size: 11px;
  line-height: 1;
  flex-shrink: 0;
}

.db-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.chevron {
  font-size: 10px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.db-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 2000;
  min-width: 240px;
  max-width: 320px;
  max-height: 320px;
  overflow: auto;
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  padding: 4px 0;
}

.db-dropdown-header {
  padding: 6px 12px 4px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.db-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

.db-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 12px;
  color: var(--text-primary);
}

.db-item:hover {
  background: var(--accent-light);
  color: var(--accent);
}

.db-item.active {
  background: var(--bg-selected);
  color: var(--accent);
  font-weight: 600;
}

.item-icon {
  font-size: 11px;
  color: #d48806;
}

.item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-check {
  color: var(--accent);
  font-size: 11px;
}

.db-footnote {
  padding: 8px 12px 6px;
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}
</style>
