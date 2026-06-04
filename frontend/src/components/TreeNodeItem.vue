<template>
  <div class="tree-node">
    <div
      class="tree-item"
      :class="{
        active: selectedNodeId === node.id,
        'db-current': node.type === 'database' && !!activeDatabase && node.database === activeDatabase
      }"
      :style="{ paddingLeft: `${depth * 12 + 4}px` }"
      @click="handleSelect"
      @contextmenu.prevent="emit('contextmenu', $event, node)"
    >
      <button v-if="hasChildren" class="toggle" @click.stop="handleExpand">
        {{ node.expanded ? '▾' : '▸' }}
      </button>
      <span v-else class="toggle-spacer" />
      <span class="node-icon" :class="iconClass" />
      <span class="label">{{ node.label }}</span>
    </div>
    <div v-if="node.expanded && node.children?.length">
      <TreeNodeItem
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :selected-node-id="selectedNodeId"
        :active-database="activeDatabase"
        @select="$emit('select', $event)"
        @expand="$emit('expand', $event)"
        @contextmenu="(e, n) => $emit('contextmenu', e, n)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TreeNode } from '@/types'

const props = defineProps<{
  node: TreeNode
  depth?: number
  selectedNodeId: string
  activeDatabase?: string
}>()

const emit = defineEmits<{
  select: [node: TreeNode]
  expand: [node: TreeNode]
  contextmenu: [event: MouseEvent, node: TreeNode]
}>()

const depth = computed(() => props.depth ?? 0)

const hasChildren = computed(() => {
  if (['database', 'folder', 'table'].includes(props.node.type)) return true
  if (props.node.type === 'columns' && props.node.label === '字段') return true
  return (props.node.children?.length ?? 0) > 0
})

const iconClass = computed(() => {
  if (props.node.type === 'folder') return `folder-${props.node.folderKind ?? 'tables'}`
  return props.node.type
})

async function handleExpand() {
  props.node.expanded = !props.node.expanded
  if (props.node.expanded && (!props.node.children?.length)) {
    emit('expand', props.node)
  }
}

function handleSelect() {
  emit('select', props.node)
}
</script>

<style scoped>
.tree-item { display: flex; align-items: center; gap: 4px; padding: 3px 6px 3px 0; margin: 1px 4px; border-radius: var(--radius); cursor: pointer; font-size: 12px; }
.tree-item:hover { background: var(--bg-hover); }
.tree-item.active { background: var(--bg-selected); color: var(--accent); }
.tree-item.db-current {
  background: var(--accent-light);
  color: var(--accent);
  box-shadow: inset 3px 0 0 var(--accent);
  font-weight: 600;
}
.toggle, .toggle-spacer { width: 14px; flex-shrink: 0; text-align: center; }
.toggle { background: transparent; border: none; color: var(--text-muted); font-size: 10px; padding: 0; }
.node-icon { width: 12px; height: 12px; border-radius: 2px; flex-shrink: 0; }
.node-icon.database { background: #ffd666; border: 1px solid #ffc53d; }
.node-icon.folder-tables, .node-icon.table { background: #91caff; border: 1px solid #69b1ff; }
.node-icon.folder-views, .node-icon.view { background: #d3adf7; border: 1px solid #b37feb; }
.node-icon.folder-procedures, .node-icon.procedure { background: #ffbb96; border: 1px solid #ff9c6e; }
.node-icon.folder-functions, .node-icon.function { background: #87e8de; border: 1px solid #5cdbd3; }
.node-icon.folder-triggers, .node-icon.trigger { background: #ffadd2; border: 1px solid #ff85c0; }
.node-icon.columns { background: #b7eb8f; border: 1px solid #95de64; }
.label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
