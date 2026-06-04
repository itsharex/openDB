<template>
  <div class="sidebar">
    <div class="section">
      <div class="section-head">
        <div class="section-title">已保存连接</div>
        <button class="section-action" title="连接管理" @click="$emit('manage-profiles')">管理</button>
      </div>
      <div v-if="profiles.length === 0" class="empty-hint">
        暂无保存的连接，连接后将自动保存到本地
      </div>
      <button
        v-for="profile in profiles"
        :key="profile.id"
        class="profile-item"
        :class="{ recent: profile.id === highlightedProfileId }"
        @click="$emit('connect-profile', profile.id)"
        @dblclick="$emit('connect-profile', profile.id)"
        @contextmenu.prevent="openProfileMenu($event, profile.id)"
      >
        <span class="dot" :class="{ online: profile.id === highlightedProfileId }" />
        <span class="profile-text">
          <span class="name">{{ profile.name }}</span>
          <span class="host">{{ profile.host }}:{{ profile.port }} · {{ profile.username }}</span>
          <span v-if="profile.database" class="db">默认库: {{ profile.database }}</span>
        </span>
      </button>
    </div>

    <div class="section">
      <div class="section-title">活动连接</div>
      <div
        v-if="activeConnectionId && !activeDatabase"
        class="db-hint-banner"
      >
        <strong>请选择数据库</strong>
        <span>点击工具栏「选择数据库」，或展开下方连接后点击数据库名称</span>
      </div>
      <div v-if="connections.length === 0" class="empty-hint">文件 → 新建连接</div>
      <div v-for="connection in connections" :key="connection.id" class="tree-group">
        <div
          class="tree-item connection"
          :class="{ active: selectedNodeId === connection.id }"
          @click="selectConnection(connection)"
          @contextmenu.prevent="openConnectionMenu($event, connection.id)"
        >
          <button class="toggle" @click.stop="toggleConnection(connection)">
            {{ expandedConnections.has(connection.id) ? '▾' : '▸' }}
          </button>
          <span class="conn-icon" />
          <span class="label">{{ connection.name }}</span>
          <button class="disconnect" title="断开" @click.stop="$emit('disconnect', connection.id)">×</button>
        </div>
        <div v-if="expandedConnections.has(connection.id)" class="tree-children">
          <TreeNodeItem
            v-for="node in treeCache[connection.id] || []"
            :key="node.id"
            :node="node"
            :selected-node-id="selectedNodeId"
            :active-database="activeDatabase"
            @select="$emit('select', $event)"
            @expand="$emit('expand', $event)"
            @contextmenu="(e, node) => $emit('contextmenu', e, node)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '@/api'
import TreeNodeItem from '@/components/TreeNodeItem.vue'
import type { ConnectionInfo, ConnectionProfile, TreeNode } from '@/types'

defineProps<{
  connections: ConnectionInfo[]
  selectedNodeId: string
  activeConnectionId?: string
  activeDatabase?: string
  highlightedProfileId?: string
}>()

const emit = defineEmits<{
  select: [node: TreeNode]
  expand: [node: TreeNode]
  disconnect: [connectionId: string]
  'connect-profile': [profileId: string]
  'manage-profiles': []
  contextmenu: [event: MouseEvent, node: TreeNode]
  'profile-contextmenu': [event: MouseEvent, profileId: string]
}>()

const profiles = ref<ConnectionProfile[]>([])
const expandedConnections = ref(new Set<string>())
const treeCache = reactive<Record<string, TreeNode[]>>({})

onMounted(loadProfiles)

async function loadProfiles() {
  try {
    profiles.value = await api.listProfiles()
  } catch {
    profiles.value = []
  }
}

async function toggleConnection(connection: ConnectionInfo) {
  if (expandedConnections.value.has(connection.id)) {
    expandedConnections.value.delete(connection.id)
    return
  }
  await expandConnection(connection.id)
}

function createDatabaseFolders(connectionId: string, database: string): TreeNode[] {
  const base = `${connectionId}:${database}`
  const folders: Array<{ kind: TreeNode['folderKind']; label: string }> = [
    { kind: 'tables', label: '表' },
    { kind: 'views', label: '视图' },
    { kind: 'procedures', label: '存储过程' },
    { kind: 'functions', label: '函数' },
    { kind: 'triggers', label: '触发器' }
  ]
  return folders.map(f => ({
    id: `${base}:${f.kind}`,
    label: f.label,
    type: 'folder',
    folderKind: f.kind,
    connectionId,
    database,
    children: [],
    expanded: false
  }))
}

function selectConnection(connection: ConnectionInfo) {
  if (!expandedConnections.value.has(connection.id)) {
    void expandConnection(connection.id)
  }
  emit('select', {
    id: connection.id,
    label: connection.name,
    type: 'connection',
    connectionId: connection.id
  })
}

function openConnectionMenu(event: MouseEvent, connectionId: string) {
  emit('contextmenu', event, {
    id: connectionId,
    label: '',
    type: 'connection',
    connectionId
  })
}

function openProfileMenu(event: MouseEvent, profileId: string) {
  emit('profile-contextmenu', event, profileId)
}

async function expandConnection(connectionId: string) {
  expandedConnections.value.add(connectionId)
  if (treeCache[connectionId]) return
  const databases = await api.listDatabases(connectionId)
  treeCache[connectionId] = databases.map(database => ({
    id: `${connectionId}:${database}`,
    label: database,
    type: 'database',
    connectionId,
    database,
    children: createDatabaseFolders(connectionId, database),
    expanded: false
  }))
}

defineExpose({ loadProfiles, treeCache, expandedConnections, expandConnection })
</script>

<style scoped>
.sidebar { padding: 0 0 12px; }
.section { margin-bottom: 8px; }
.section-head { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px 4px; }
.section-title { font-size: 10px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; }
.section-action { border: none; background: transparent; color: var(--accent); font-size: 11px; padding: 0; }
.section-action:hover { text-decoration: underline; }
.db-hint-banner {
  margin: 4px 8px 8px;
  padding: 8px 10px;
  border: 1px solid #ffe58f;
  border-radius: var(--radius);
  background: #fffbe6;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #ad6800;
  line-height: 1.4;
}
.db-hint-banner strong { font-size: 12px; }
.empty-hint { padding: 8px 12px; font-size: 11px; color: var(--text-muted); }
.profile-item { width: calc(100% - 8px); margin: 2px 4px; display: flex; align-items: flex-start; gap: 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-primary); padding: 8px; text-align: left; }
.profile-item:hover { border-color: var(--accent); background: var(--accent-light); }
.profile-item.recent { border-color: #b3ccff; background: var(--accent-light); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--text-muted); flex-shrink: 0; margin-top: 4px; }
.dot.online { background: var(--success); }
.profile-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.name { font-size: 12px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.host { font-size: 10px; color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.db { font-size: 10px; color: var(--accent); }
.tree-item { display: flex; align-items: center; gap: 4px; padding: 4px 8px; margin: 1px 4px; border-radius: var(--radius); cursor: pointer; font-size: 12px; }
.tree-item:hover { background: var(--bg-hover); }
.tree-item.active { background: var(--bg-selected); color: var(--accent); }
.toggle, .disconnect { background: transparent; border: none; color: var(--text-muted); font-size: 10px; padding: 0 2px; }
.disconnect { margin-left: auto; opacity: 0; font-size: 14px; }
.tree-item:hover .disconnect { opacity: 1; }
.conn-icon { width: 14px; height: 14px; border-radius: 3px; background: var(--accent); flex-shrink: 0; }
.label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-children { margin-left: 8px; }
</style>
