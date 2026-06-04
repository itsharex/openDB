<template>
  <div v-if="visible" class="context-menu" :style="{ top: `${y}px`, left: `${x}px` }" @click.stop>
    <template v-for="item in items" :key="item.id">
      <div v-if="item.divider" class="divider" />
      <button
        v-else
        class="menu-item"
        :class="{ danger: item.danger }"
        :disabled="item.disabled"
        @click="onClick(item.action)"
      >
        {{ item.label }}
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { ContextMenuItem } from '@/types/features'

defineProps<{
  visible: boolean
  x: number
  y: number
  items: ContextMenuItem[]
}>()

const emit = defineEmits<{ action: [action: string]; close: [] }>()

function onClick(action: string) {
  emit('action', action)
  emit('close')
}
</script>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 5000;
  min-width: 180px;
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  padding: 4px 0;
}
.menu-item {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--text-primary);
}
.menu-item:hover:not(:disabled) { background: var(--accent-light); color: var(--accent); }
.menu-item.danger { color: var(--danger); }
.menu-item:disabled { opacity: 0.4; cursor: not-allowed; }
.divider { height: 1px; background: var(--border); margin: 4px 0; }
</style>
