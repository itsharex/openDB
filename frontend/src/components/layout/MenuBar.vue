<template>
  <nav class="menu-bar" @mouseleave="closeMenu">
    <div class="menu-brand">openDB</div>
    <div
      v-for="group in menuGroups"
      :key="group.id"
      class="menu-group"
      :class="{ open: openMenuId === group.id }"
    >
      <button
        class="menu-trigger"
        :class="{ active: openMenuId === group.id }"
        @click.stop="toggleMenu(group.id)"
        @mouseenter="onMenuEnter(group.id)"
      >
        {{ group.label }}
      </button>
      <div v-if="openMenuId === group.id" class="menu-dropdown">
        <template v-for="item in group.items" :key="item.id">
          <div v-if="item.divider" class="menu-divider" />
          <button
            v-else
            class="menu-item"
            :class="{ checked: isChecked(item) }"
            :disabled="isDisabled(item)"
            @click="onItemClick(item)"
          >
            <span class="menu-item-label">
              <span v-if="isChecked(item)" class="check-mark">✓</span>
              {{ item.label }}
            </span>
            <span v-if="item.shortcut" class="menu-shortcut">{{ item.shortcut }}</span>
          </button>
        </template>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { menuGroups, type MenuItem } from '@/config/menus'

const props = defineProps<{
  panelState: {
    objectBrowser: boolean
    resultPanel: boolean
    aiPanel: boolean
    objectDetail?: boolean
  }
  hasConnection: boolean
}>()

const emit = defineEmits<{
  action: [action: string]
}>()

const openMenuId = ref<string | null>(null)

function toggleMenu(id: string) {
  openMenuId.value = openMenuId.value === id ? null : id
}

function onMenuEnter(id: string) {
  if (openMenuId.value) {
    openMenuId.value = id
  }
}

function closeMenu() {
  openMenuId.value = null
}

function isChecked(item: MenuItem) {
  if (item.action === 'toggleObjectBrowser') return props.panelState.objectBrowser
  if (item.action === 'toggleResultPanel') return props.panelState.resultPanel
  if (item.action === 'toggleAiPanel') return props.panelState.aiPanel
  if (item.action === 'toggleObjectDetail') return props.panelState.objectDetail ?? true
  return item.checked
}

function isDisabled(item: MenuItem) {
  if (item.action === 'disconnect' || item.action === 'runSql' || item.action === 'explainSql') {
    return !props.hasConnection
  }
  return item.disabled
}

function onItemClick(item: MenuItem) {
  if (item.action) {
    emit('action', item.action)
  }
  closeMenu()
}
</script>

<style scoped>
.menu-bar {
  height: var(--menubar-height);
  display: flex;
  align-items: stretch;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  padding: 0 4px;
  user-select: none;
  flex-shrink: 0;
}

.menu-brand {
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-weight: 700;
  font-size: 12px;
  color: var(--accent);
  letter-spacing: -0.02em;
  border-right: 1px solid var(--border);
  margin-right: 2px;
}

.menu-group {
  position: relative;
}

.menu-trigger {
  height: 100%;
  padding: 0 10px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
}

.menu-trigger:hover,
.menu-trigger.active {
  background: var(--bg-hover);
}

.menu-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  min-width: 220px;
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  padding: 4px 0;
  z-index: 100;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 6px 28px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  text-align: left;
}

.menu-item:hover:not(:disabled) {
  background: var(--accent-light);
  color: var(--accent);
}

.menu-item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.menu-item.checked {
  padding-left: 12px;
}

.check-mark {
  display: inline-block;
  width: 14px;
  color: var(--accent);
  font-weight: 700;
}

.menu-item-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

.menu-shortcut {
  color: var(--text-muted);
  font-size: 11px;
  margin-left: 24px;
}

.menu-divider {
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}
</style>
