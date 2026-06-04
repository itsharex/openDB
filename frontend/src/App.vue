<template>
  <div class="app-shell" @click="closeContextMenu">
    <MenuBar
      :panel-state="{ ...panelState, objectDetail: panelState.objectDetail }"
      :has-connection="!!activeConnectionId"
      @action="handleMenuAction"
    />

    <ToolBar
      ref="toolbarRef"
      :connection-name="activeConnectionName"
      :database="activeDatabase"
      :databases="schemaDatabases"
      :databases-loading="databasesLoading"
      :ai-online="!!(aiStatus?.enabled && aiStatus?.configured)"
      :ai-label="aiStatusLabel"
      :ai-panel-visible="panelState.aiPanel"
      :has-connection="!!activeConnectionId"
      @action="handleMenuAction"
      @select-database="selectDatabase"
      @refresh-databases="loadSchemaContext"
    />

    <div class="workspace">
      <aside v-show="panelState.objectBrowser" class="object-panel" :style="{ width: `${sidebarWidth}px` }">
        <div class="panel-title-bar">
          <span>对象浏览器</span>
          <button class="panel-action" title="刷新" @click="refreshAll">↻</button>
        </div>
        <div class="panel-content scrollbar">
          <SidebarTree
            ref="sidebarRef"
            :connections="connections"
            :selected-node-id="selectedNodeId"
            :active-connection-id="activeConnectionId"
            :active-database="activeDatabase"
            @select="handleNodeSelect"
            @expand="handleNodeExpand"
            @disconnect="handleDisconnect"
            @connect-profile="connectProfile"
            :highlighted-profile-id="appSettings.lastProfileId"
            @manage-profiles="showConnectionManager = true"
            @contextmenu="openContextMenu"
            @profile-contextmenu="openProfileContextMenu"
          />
        </div>
      </aside>

      <PanelSplitter
        v-show="panelState.objectBrowser"
        direction="vertical"
        @resize-start="sidebarResizeStart = sidebarWidth"
        @resize="delta => sidebarWidth = clampWidth(sidebarResizeStart + delta, SIDEBAR_MIN, SIDEBAR_MAX)"
      />

      <main class="center-panel">
        <WorkspacePanel
          ref="workspacePanelRef"
          :tabs="queryTabs"
          :active-tab-id="activeTabId"
          @switch-tab="switchTab"
          @close-tab="closeTab"
          @new-tab="newQueryTab"
        >
          <div class="center-upper" :style="{ flex: upperFlex }">
            <section class="editor-section">
              <SqlQuickCommands
                :database="activeDatabase"
                :table="selectedTable"
                :current-sql="sqlText"
                @apply="applyQuickSql"
              />
            <SqlEditor
              ref="sqlEditorRef"
              v-model="sqlText"
              :databases="schemaDatabases"
              :schema="activeSchema"
              :database-type="activeConnectionType"
              :has-connection="!!activeConnectionId"
              @run="mode => runSql(undefined, false, mode)"
            />
            </section>

            <ObjectDetailPanel
              v-if="panelState.objectDetail && objectSelection"
              :selection="objectSelection"
              @open-in-editor="sql => (sqlText = sql)"
            />
          </div>

          <PanelSplitter
            v-show="panelState.resultPanel"
            direction="horizontal"
            @resize-start="startCenterResize"
            @resize="resizeCenterPanels"
          />

          <section
            v-show="panelState.resultPanel"
            class="result-section"
            :style="{ flex: resultFlex }"
          >
            <div class="result-header">
              <div class="result-header-left">
                <span>结果</span>
                <div v-if="queryResult?.type === 'SELECT'" class="result-view-tabs">
                  <button
                    class="result-tab"
                    :class="{ active: resultViewTab === 'data' }"
                    @click="resultViewTab = 'data'"
                  >
                    数据
                  </button>
                  <button
                    class="result-tab"
                    :class="{ active: resultViewTab === 'analytics' }"
                    @click="resultViewTab = 'analytics'"
                  >
                    智能分析
                  </button>
                </div>
              </div>
              <span v-if="queryResult?.executionTimeMs != null" class="result-meta">
                {{ queryResult.rowCount ?? 0 }} 行 · {{ queryResult.executionTimeMs }} ms
              </span>
            </div>
            <DataTable
              v-show="resultViewTab === 'data'"
              :result="queryResult"
              :error="queryError"
              :editable="!!editContext"
              :edit-context="editContext"
              @refresh="refreshQueryResult"
              @selection-change="selectionSubset = $event"
              @notify="(msg, type) => showToast(type || 'info', msg)"
            />
            <ResultAnalyticsPanel
              v-show="resultViewTab === 'analytics'"
              :result="queryResult"
              :selection="selectionSubset"
              @notify="(msg, type) => showToast(type || 'info', msg)"
            />
          </section>
        </WorkspacePanel>
      </main>

      <PanelSplitter
        v-show="panelState.aiPanel"
        direction="vertical"
        invert
        @resize-start="aiPanelResizeStart = aiPanelWidth"
        @resize="delta => aiPanelWidth = clampWidth(aiPanelResizeStart + delta, AI_PANEL_MIN, AI_PANEL_MAX)"
      />
      <aside v-show="panelState.aiPanel" class="ai-panel" :style="{ width: `${aiPanelWidth}px` }">
        <div class="panel-title-bar"><span>AI 助手</span></div>
        <div class="panel-content scrollbar ai-content">
          <AiPanel
            ref="aiPanelRef"
            :connection-id="activeConnectionId"
            :database="activeDatabase"
            :sql-text="sqlText"
            :ai-status="aiStatus"
            @apply-sql="applySqlFromAi"
            @open-settings="showAiSettings = true"
            @status-change="aiWorkingStatus = $event"
          />
        </div>
      </aside>
    </div>

    <StatusBar
      :connection-status="statusConnection"
      :has-connection="!!activeConnectionId"
      :database="activeDatabase"
      :database-type-label="activeConnectionTypeLabel"
      :executing="executing"
      :ai-status="aiWorkingStatus"
      :result-message="statusResultMessage"
      @select-database="promptSelectDatabase"
    />

    <ConnectionDialog
      v-model="showConnectionDialog"
      :database-types="databaseTypes"
      :editing-profile="editingProfile"
      @created="handleConnectionCreated"
      @updated="onProfileUpdated"
      @notify="p => showToast(p.type, p.message)"
    />
    <ConnectionManagerDialog
      v-model="showConnectionManager"
      :last-profile-id="appSettings.lastProfileId"
      @connect="connectProfile"
      @edit="openEditProfile"
      @create="openNewConnection"
      @changed="sidebarRef?.loadProfiles()"
    />
    <CreateDatabaseDialog v-model="showCreateDatabase" :connection-id="activeConnectionId" @created="onDatabaseCreated" />
    <CreateTableDialog v-model="showCreateTable" :connection-id="activeConnectionId" :database="activeDatabase" @created="onTableCreated" />
    <SettingsDialog v-model="showSettings" @saved="onSettingsSaved" />
    <AiSettingsDialog v-model="showAiSettings" @saved="onAiSettingsSaved" />
    <FindReplaceDialog v-model="showFindReplace" @find="t => sqlEditorRef?.findNext(t)" @replace="(f,r,a) => sqlEditorRef?.replaceText(f,r,a)" />
    <QueryHistoryDialog v-model="showQueryHistory" @select="sql => (sqlText = sql)" />
    <TableDesignerDialog v-model="showTableDesigner" :connection-id="activeConnectionId" :database="activeDatabase" @created="onTableCreated" />
    <ErDiagramDialog v-model="showErDiagram" :connection-id="activeConnectionId" :database="activeDatabase" />
    <ImportDataDialog v-model="showImportData" :connection-id="activeConnectionId" :database="activeDatabase" :default-table="selectedTable" @imported="n => showToast('success', `已导入 ${n} 行`)" />
    <DataTransferDialog v-model="showDataTransfer" :connection-id="activeConnectionId" :database="activeDatabase" :table="selectedTable" @done="n => showToast('success', `已传输 ${n} 行`)" />
    <SchemaSyncDialog v-model="showSchemaSync" :connection-id="activeConnectionId" :database="activeDatabase" />
    <DataCompareDialog v-model="showDataCompare" :connection-id="activeConnectionId" :database="activeDatabase" :table="selectedTable" />
    <PluginManagerDialog v-model="showPluginManager" />

    <AppDialog v-model="showAbout" title="关于 openDB">
      <p><strong>openDB</strong> — 开源 AI 数据库管理工具</p>
      <p>版本：0.1.0 · 支持 MySQL / PostgreSQL / Oracle / H2</p>
    </AppDialog>
    <AppDialog v-model="showShortcuts" title="快捷键参考">
      <table class="shortcut-table"><tbody><tr v-for="item in shortcuts" :key="item.key"><td>{{ item.label }}</td><td><kbd>{{ item.key }}</kbd></td></tr></tbody></table>
    </AppDialog>
    <AppDialog v-model="showDocs" title="使用文档">
      <p>1. 文件 → 新建连接，或从「已保存连接」快速连接</p>
      <p>2. 展开对象浏览器中的数据库，浏览表/视图/存储过程等</p>
      <p>3. 查询 → 运行，或 Ctrl+Enter 执行 SQL</p>
      <p>4. 右键对象可预览数据、查看 DDL、导出 CSV</p>
      <p>5. 数据库菜单支持新建库/表、备份、清空、删除等操作</p>
    </AppDialog>

    <ContextMenu :visible="contextMenu.visible" :x="contextMenu.x" :y="contextMenu.y" :items="contextMenu.items" @action="handleContextAction" @close="closeContextMenu" />
    <input ref="fileInputRef" type="file" accept=".sql,.txt" hidden @change="onImportFile" />

    <div v-if="toast" class="toast" :class="toast.type">{{ toast.message }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from '@/api'
import AiPanel from '@/components/AiPanel.vue'
import ConnectionDialog from '@/components/ConnectionDialog.vue'
import ConnectionManagerDialog from '@/components/dialogs/ConnectionManagerDialog.vue'
import ContextMenu from '@/components/ContextMenu.vue'
import DataTable from '@/components/DataTable.vue'
import ResultAnalyticsPanel from '@/components/ResultAnalyticsPanel.vue'
import ObjectDetailPanel from '@/components/ObjectDetailPanel.vue'
import SidebarTree from '@/components/SidebarTree.vue'
import SqlEditor from '@/components/SqlEditor.vue'
import SqlQuickCommands from '@/components/SqlQuickCommands.vue'
import CreateDatabaseDialog from '@/components/dialogs/CreateDatabaseDialog.vue'
import CreateTableDialog from '@/components/dialogs/CreateTableDialog.vue'
import FindReplaceDialog from '@/components/dialogs/FindReplaceDialog.vue'
import QueryHistoryDialog from '@/components/dialogs/QueryHistoryDialog.vue'
import TableDesignerDialog from '@/components/dialogs/TableDesignerDialog.vue'
import ErDiagramDialog from '@/components/dialogs/ErDiagramDialog.vue'
import ImportDataDialog from '@/components/dialogs/ImportDataDialog.vue'
import DataTransferDialog from '@/components/dialogs/DataTransferDialog.vue'
import SchemaSyncDialog from '@/components/dialogs/SchemaSyncDialog.vue'
import DataCompareDialog from '@/components/dialogs/DataCompareDialog.vue'
import PluginManagerDialog from '@/components/dialogs/PluginManagerDialog.vue'
import SettingsDialog from '@/components/dialogs/SettingsDialog.vue'
import AiSettingsDialog from '@/components/dialogs/AiSettingsDialog.vue'
import AppDialog from '@/components/layout/AppDialog.vue'
import MenuBar from '@/components/layout/MenuBar.vue'
import StatusBar from '@/components/layout/StatusBar.vue'
import ToolBar from '@/components/layout/ToolBar.vue'
import WorkspacePanel, { type QueryTab } from '@/components/layout/WorkspacePanel.vue'
import PanelSplitter from '@/components/layout/PanelSplitter.vue'
import { loadSettings, saveSettings } from '@/composables/useAppSettings'
import { applyBackgroundTheme } from '@/composables/useBackgroundTheme'
import { addQueryHistory } from '@/composables/useQueryHistory'
import type { AppSettings, ContextMenuItem, ObjectSelection, TableEditContext } from '@/types/features'
import type { AiStatus, ConnectionInfo, ConnectionProfile, ConnectionRequest, DatabaseTypeInfo, SchemaSummary, SqlExecuteResponse, TreeNode } from '@/types'
import { downloadText, exportResultToCsv } from '@/utils/exportCsv'
import { isExplainResult } from '@/utils/explainAnalytics'
import { getDatabaseTypeLabel, quoteIdentifier } from '@/utils/sqlDialect'
import { formatAiSql, formatSqlBasic } from '@/utils/sqlFormatter'
import { resolveSqlStatements } from '@/utils/splitSqlStatements'

const connections = ref<ConnectionInfo[]>([])
const databaseTypes = ref<DatabaseTypeInfo[]>([])
const aiStatus = ref<AiStatus | null>(null)
const appSettings = ref<AppSettings>(loadSettings())

const showConnectionDialog = ref(false)
const showConnectionManager = ref(false)
const editingProfile = ref<ConnectionProfile | null>(null)
const showCreateDatabase = ref(false)
const showCreateTable = ref(false)
const showSettings = ref(false)
const showAiSettings = ref(false)
const showFindReplace = ref(false)
const showQueryHistory = ref(false)
const showTableDesigner = ref(false)
const showErDiagram = ref(false)
const showImportData = ref(false)
const showDataTransfer = ref(false)
const showSchemaSync = ref(false)
const showDataCompare = ref(false)
const showPluginManager = ref(false)
const showAbout = ref(false)
const showShortcuts = ref(false)
const showDocs = ref(false)

const selectedNodeId = ref('')
const activeConnectionId = ref('')
const activeDatabase = ref('')
const selectedTable = ref('')
const previewTableName = ref('')
const lastQuerySql = ref('')
const editContext = ref<TableEditContext | null>(null)
const objectSelection = ref<ObjectSelection | null>(null)

const queryResult = ref<SqlExecuteResponse | null>(null)
const queryError = ref('')
const resultViewTab = ref<'data' | 'analytics'>('data')
const selectionSubset = ref<{ rows: Record<string, unknown>[]; columns: string[] } | null>(null)
const batchSummary = ref('')
const executing = ref(false)
const aiWorkingStatus = ref('')
const toast = ref<{ type: 'success' | 'error' | 'info'; message: string } | null>(null)
const schemaDatabases = ref<string[]>([])
const databasesLoading = ref(false)
const activeSchema = ref<SchemaSummary | null>(null)

const aiPanelRef = ref<InstanceType<typeof AiPanel> | null>(null)
const sqlEditorRef = ref<InstanceType<typeof SqlEditor> | null>(null)
const sidebarRef = ref<InstanceType<typeof SidebarTree> | null>(null)
const toolbarRef = ref<InstanceType<typeof ToolBar> | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const panelState = ref({ objectBrowser: true, objectDetail: true, resultPanel: true, aiPanel: true })
const SIDEBAR_MIN = 180
const SIDEBAR_MAX = 520
const AI_PANEL_MIN = 260
const AI_PANEL_MAX = 640
const MIN_CENTER_FLEX = 0.15
const sidebarWidth = ref(260)
const aiPanelWidth = ref(340)
const sidebarResizeStart = ref(260)
const aiPanelResizeStart = ref(340)
const upperFlex = ref('1')
const resultFlex = ref('1')
const workspacePanelRef = ref<InstanceType<typeof WorkspacePanel> | null>(null)
let centerResizeUpperFlex = 1
let centerResizeResultFlex = 1
let centerResizeBodyHeight = 600
const queryTabs = ref<QueryTab[]>([{ id: 'tab-1', title: '查询 1', sql: 'SELECT 1;' }])
const activeTabId = ref('tab-1')
let tabCounter = 1

const contextMenu = ref({ visible: false, x: 0, y: 0, items: [] as ContextMenuItem[], node: null as TreeNode | null, profileId: '' })

const sqlText = computed({
  get: () => queryTabs.value.find(t => t.id === activeTabId.value)?.sql ?? '',
  set: (value: string) => { const tab = queryTabs.value.find(t => t.id === activeTabId.value); if (tab) tab.sql = value }
})

const activeConnectionName = computed(() => connections.value.find(c => c.id === activeConnectionId.value)?.name ?? '')
const activeConnection = computed(() => connections.value.find(c => c.id === activeConnectionId.value))
const activeConnectionType = computed(() => activeConnection.value?.type ?? 'MYSQL')
const activeConnectionTypeLabel = computed(() => getDatabaseTypeLabel(activeConnectionType.value))
const statusConnection = computed(() => activeConnectionId.value ? `已连接: ${activeConnectionName.value}` : '未连接')
const aiStatusLabel = computed(() => {
  if (!aiStatus.value?.enabled) return '未启用'
  if (!aiStatus.value.configured) return '未配置'
  return `${aiStatus.value.providerLabel} · ${aiStatus.value.model}`
})
const statusResultMessage = computed(() => batchSummary.value || queryError.value || queryResult.value?.message || '')

const shortcuts = [
  { label: '新建连接', key: 'Ctrl+N' }, { label: '新建查询', key: 'Ctrl+T' },
  { label: '运行 SQL', key: 'Ctrl+Enter' }, { label: '运行选中', key: 'Ctrl+Shift+Enter' },
  { label: '格式化', key: 'Ctrl+Shift+F' }, { label: '查找', key: 'Ctrl+F' }, { label: '刷新', key: 'F5' }
]

onMounted(async () => {
  applyBackgroundTheme(appSettings.value)
  try {
    await Promise.all([loadConnections(), loadDatabaseTypes(), loadAiStatus()])
    await sidebarRef.value?.loadProfiles()
    const profiles = await api.listProfiles()
    if (profiles.length > 0 && !activeConnectionId.value) {
      showToast('info', `已加载 ${profiles.length} 个保存的连接，点击左侧即可连接`)
    }
  }
  catch (e) { showToast('error', e instanceof Error ? e.message : '初始化失败') }
  window.addEventListener('keydown', handleGlobalKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', handleGlobalKeydown))
watch([activeConnectionId, activeDatabase], loadSchemaContext)

async function loadConnections() { connections.value = await api.listConnections() }
async function loadDatabaseTypes() { databaseTypes.value = await api.getDatabaseTypes() }
async function loadAiStatus() { aiStatus.value = await api.getAiStatus() }

async function loadSchemaContext() {
  if (!activeConnectionId.value) { schemaDatabases.value = []; activeSchema.value = null; return }
  databasesLoading.value = true
  try {
    schemaDatabases.value = await api.listDatabases(activeConnectionId.value)
    activeSchema.value = activeDatabase.value ? await api.getSchema(activeConnectionId.value, activeDatabase.value) : null
  } catch {
    schemaDatabases.value = []
    activeSchema.value = null
  } finally {
    databasesLoading.value = false
  }
}

function selectDatabase(database: string) {
  if (!activeConnectionId.value || !database) return
  activeDatabase.value = database
  selectedNodeId.value = `${activeConnectionId.value}:${database}`
  objectSelection.value = null
  sidebarRef.value?.expandConnection(activeConnectionId.value)
  persistSession(appSettings.value.lastProfileId, database)
  loadSchemaContext()
}

function promptSelectDatabase() {
  panelState.value.objectBrowser = true
  toolbarRef.value?.openDatabaseSelector()
  toolbarRef.value?.pulseDatabaseSelector()
  showToast('info', '请在工具栏选择数据库，或在左侧展开连接后点击数据库名称')
}

async function ensureDatabaseAfterConnect(connection: ConnectionInfo) {
  await loadSchemaContext()
  await sidebarRef.value?.expandConnection(connection.id)
  if (connection.database && schemaDatabases.value.includes(connection.database)) {
    selectDatabase(connection.database)
    return
  }
  if (schemaDatabases.value.length === 1) {
    selectDatabase(schemaDatabases.value[0])
    return
  }
  if (!activeDatabase.value && schemaDatabases.value.length > 1) {
    showToast('info', '连接成功，请选择要操作的数据库')
    promptSelectDatabase()
  }
}

async function refreshAll() {
  await loadConnections()
  await loadSchemaContext()
  sidebarRef.value?.loadProfiles()
  showToast('success', '已刷新')
}

function showToast(type: 'success' | 'error' | 'info', message: string) {
  toast.value = { type, message }
  setTimeout(() => { toast.value = null }, 3000)
}

function confirmAction(message: string) {
  if (!appSettings.value.confirmDestructive) return true
  return window.confirm(message)
}

function handleMenuAction(action: string) {
  const map: Record<string, () => void> = {
    newConnection: () => { openNewConnection() },
    manageProfiles: () => { showConnectionManager.value = true },
    disconnect: () => { if (activeConnectionId.value) handleDisconnect(activeConnectionId.value) },
    refreshConnections: () => { refreshAll() },
    importSql: () => { fileInputRef.value?.click() },
    exportResult: () => { exportCurrentResult() },
    exitApp: () => { showToast('info', 'Web 版请关闭浏览器标签页') },
    editorUndo: () => { sqlEditorRef.value?.undo() },
    editorRedo: () => { sqlEditorRef.value?.redo() },
    copySql: () => { navigator.clipboard.writeText(sqlText.value).then(() => showToast('success', '已复制')) },
    selectAll: () => { sqlEditorRef.value?.selectAll() },
    findReplace: () => { showFindReplace.value = true },
    formatSql: () => { sqlText.value = formatSqlBasic(sqlText.value) },
    toggleObjectBrowser: () => { panelState.value.objectBrowser = !panelState.value.objectBrowser },
    toggleObjectDetail: () => { panelState.value.objectDetail = !panelState.value.objectDetail },
    toggleResultPanel: () => { panelState.value.resultPanel = !panelState.value.resultPanel },
    toggleAiPanel: () => { panelState.value.aiPanel = !panelState.value.aiPanel },
    refreshSchema: () => { loadSchemaContext().then(() => showToast('success', '结构已刷新')) },
    fullscreen: () => { document.documentElement.requestFullscreen?.() },
    newQuery: () => { newQueryTab() },
    runSql: () => { runSql(undefined, false, 'all') },
    runSelected: () => { runSql(undefined, false, 'selection') },
    explainSql: () => { explainSql() },
    queryHistory: () => { showQueryHistory.value = true },
    newDatabase: () => { requireConnection(() => { showCreateDatabase.value = true }) },
    dropDatabase: () => { requireConnection(() => dropDatabase()) },
    newTable: () => { requireDatabase(() => { showCreateTable.value = true }) },
    designTable: () => { requireDatabase(() => { if (selectedTable.value) setObjectSelection('table', selectedTable.value); panelState.value.objectDetail = true }) },
    exportTableData: () => { requireDatabase(() => exportTableData()) },
    truncateTable: () => { requireDatabase(() => truncateTable()) },
    dropTable: () => { requireDatabase(() => dropTableAction()) },
    backupDatabase: () => { requireDatabase(() => backupDatabase()) },
    importData: () => { requireDatabase(() => { showImportData.value = true }) },
    tableDesigner: () => { requireDatabase(() => { showTableDesigner.value = true }) },
    erDiagram: () => { requireDatabase(() => { showErDiagram.value = true }) },
    dataTransfer: () => { requireConnection(() => { showDataTransfer.value = true }) },
    schemaSync: () => { requireConnection(() => { showSchemaSync.value = true }) },
    dataCompare: () => { requireConnection(() => { showDataCompare.value = true }) },
    pluginManager: () => { showPluginManager.value = true },
    options: () => { showSettings.value = true },
    aiSettings: () => { showAiSettings.value = true },
    resetLayout: () => {
      panelState.value = { objectBrowser: true, objectDetail: true, resultPanel: true, aiPanel: true }
      sidebarWidth.value = 260
      aiPanelWidth.value = 340
      upperFlex.value = '1'
      resultFlex.value = '1'
    },
    aiGenerate: () => {
      panelState.value.aiPanel = true
      aiPanelRef.value?.sendPrompt('请根据当前数据库结构，生成一个常用查询 SQL', { autoApplySql: true })
    },
    aiExplain: () => { panelState.value.aiPanel = true; aiPanelRef.value?.sendPrompt(`请解释以下 SQL：\n${sqlText.value}`) },
    aiOptimize: () => {
      panelState.value.aiPanel = true
      aiPanelRef.value?.sendPrompt(`请优化以下 SQL，只返回优化后的 SQL：\n${sqlText.value}`, { autoApplySql: true })
    },
    showAbout: () => { showAbout.value = true },
    showShortcuts: () => { showShortcuts.value = true },
    showDocs: () => { showDocs.value = true },
    comingSoon: () => { showToast('info', '功能即将推出') }
  }
  ;(map[action] ?? map.comingSoon)()
}

function persistSession(profileId?: string, database?: string) {
  if (profileId) appSettings.value.lastProfileId = profileId
  if (database) appSettings.value.lastDatabase = database
  saveSettings(appSettings.value)
}

function openNewConnection() {
  editingProfile.value = null
  showConnectionDialog.value = true
}

function openEditProfile(profile: ConnectionProfile) {
  editingProfile.value = profile
  showConnectionManager.value = false
  showConnectionDialog.value = true
}

async function onProfileUpdated() {
  await sidebarRef.value?.loadProfiles()
  showToast('success', '连接配置已更新')
}

function requireConnection(fn: () => void) {
  if (!activeConnectionId.value) {
    showToast('error', '请先连接数据库')
    showConnectionManager.value = true
    return
  }
  fn()
}

function requireDatabase(fn: () => void) {
  if (!activeDatabase.value) {
    promptSelectDatabase()
    return
  }
  fn()
}

function handleGlobalKeydown(e: KeyboardEvent) {
  const mod = e.metaKey || e.ctrlKey
  if (mod && e.key === 'n') { e.preventDefault(); openNewConnection() }
  if (mod && e.key === 't') { e.preventDefault(); newQueryTab() }
  if (mod && e.key === 'f') { e.preventDefault(); showFindReplace.value = true }
  if (mod && e.key === 'a') { e.preventDefault(); sqlEditorRef.value?.selectAll() }
  if (mod && e.shiftKey && e.key === 'Enter') { e.preventDefault(); runSql(undefined, false, 'selection') }
  if (mod && e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); runSql(undefined, false, 'all') }
  if (mod && e.shiftKey && e.key === 'F') { e.preventDefault(); sqlText.value = formatSqlBasic(sqlText.value) }
  if (e.key === 'F5') { e.preventDefault(); refreshAll() }
  if (e.key === 'F11') { e.preventDefault(); document.documentElement.requestFullscreen?.() }
}

function newQueryTab() {
  tabCounter += 1
  const id = `tab-${tabCounter}`
  queryTabs.value.push({ id, title: `查询 ${tabCounter}`, sql: '-- 新建查询\n' })
  activeTabId.value = id
}
function switchTab(id: string) { activeTabId.value = id }
function closeTab(id: string) {
  if (queryTabs.value.length <= 1) return
  const index = queryTabs.value.findIndex(t => t.id === id)
  queryTabs.value.splice(index, 1)
  if (activeTabId.value === id) activeTabId.value = queryTabs.value[Math.max(0, index - 1)].id
}

function upsertActiveConnection(connection: ConnectionInfo) {
  if (connection.profileId) {
    connections.value = connections.value.filter(c => c.profileId !== connection.profileId)
  } else {
    connections.value = connections.value.filter(c => c.id !== connection.id)
  }
  connections.value.push(connection)
}

async function connectProfile(profileId: string) {
  try {
    const connection = await api.connectProfile(profileId)
    upsertActiveConnection(connection)
    activeConnectionId.value = connection.id
    activeDatabase.value = ''
    persistSession(profileId)
    await ensureDatabaseAfterConnect(connection)
    if (appSettings.value.lastDatabase && schemaDatabases.value.includes(appSettings.value.lastDatabase)) {
      selectDatabase(appSettings.value.lastDatabase)
    }
    showToast('success', `已连接 ${connection.name}`)
  } catch (e) { showToast('error', e instanceof Error ? e.message : '连接失败') }
}

async function handleConnectionCreated(connection: ConnectionInfo, _form: ConnectionRequest, profileId?: string) {
  upsertActiveConnection(connection)
  activeConnectionId.value = connection.id
  activeDatabase.value = ''
  persistSession(profileId || connection.profileId)
  await sidebarRef.value?.loadProfiles()
  await ensureDatabaseAfterConnect(connection)
  showToast('success', `已连接 ${connection.name}`)
}

async function handleDisconnect(connectionId: string) {
  await api.disconnect(connectionId)
  connections.value = connections.value.filter(c => c.id !== connectionId)
  if (activeConnectionId.value === connectionId) { activeConnectionId.value = ''; activeDatabase.value = ''; objectSelection.value = null }
  showToast('success', '连接已断开')
}

async function handleNodeExpand(node: TreeNode) {
  if (!node.connectionId) return
  if (node.type === 'connection') {
    const databases = await api.listDatabases(node.connectionId)
    node.children = databases.map(database => ({
      id: `${node.connectionId}:${database}`, label: database, type: 'database' as const,
      connectionId: node.connectionId, database,
      children: createFolders(node.connectionId!, database), expanded: false
    }))
  }
  if (node.type === 'database' && node.database) {
    node.children = createFolders(node.connectionId, node.database)
  }
  if (node.type === 'folder' && node.database && node.folderKind) {
    node.children = await loadFolderObjects(node.connectionId!, node.database, node.folderKind)
  }
  if (node.type === 'table' && node.database && node.table) {
    node.children = [{
      id: `${node.connectionId}:${node.database}:${node.table}:columns`, label: '字段', type: 'columns' as const,
      connectionId: node.connectionId, database: node.database, table: node.table, children: [], expanded: false
    }]
  }
  if (node.type === 'columns' && node.database && node.table) {
    const columns = await api.listColumns(node.connectionId!, node.database, node.table)
    node.children = columns.map(col => ({
      id: `${node.connectionId}:${node.database}:${node.table}:${col.name}`,
      label: `${col.name} (${col.type})`, type: 'columns' as const,
      connectionId: node.connectionId, database: node.database, table: node.table
    }))
  }
}

function createFolders(connectionId: string, database: string): TreeNode[] {
  return (['tables', 'views', 'procedures', 'functions', 'triggers'] as const).map(kind => ({
    id: `${connectionId}:${database}:${kind}`,
    label: { tables: '表', views: '视图', procedures: '存储过程', functions: '函数', triggers: '触发器' }[kind],
    type: 'folder' as const, folderKind: kind, connectionId, database, children: [], expanded: false
  }))
}

async function loadFolderObjects(connectionId: string, database: string, kind: NonNullable<TreeNode['folderKind']>) {
  const map = {
    tables: () => api.listTables(connectionId, database).then(items => items.map(t => ({ name: t.name, type: 'table' as const }))),
    views: () => api.listViews(connectionId, database).then(items => items.map(v => ({ name: v.name, type: 'view' as const }))),
    procedures: () => api.listProcedures(connectionId, database).then(items => items.map(v => ({ name: v.name, type: 'procedure' as const }))),
    functions: () => api.listFunctions(connectionId, database).then(items => items.map(v => ({ name: v.name, type: 'function' as const }))),
    triggers: () => api.listTriggers(connectionId, database).then(items => items.map(v => ({ name: v.name, type: 'trigger' as const })))
  }
  const items = await map[kind]()
  return items.map(item => ({
    id: `${connectionId}:${database}:${item.type}:${item.name}`,
    label: item.name, type: item.type, objectName: item.name,
    connectionId, database, table: item.type === 'table' ? item.name : undefined,
    children: item.type === 'table' ? [{
      id: `${connectionId}:${database}:${item.name}:columns`, label: '字段', type: 'columns' as const,
      connectionId, database, table: item.name, children: [], expanded: false
    }] : [],
    expanded: false
  }))
}

async function handleNodeSelect(node: TreeNode) {
  selectedNodeId.value = node.id
  if (node.connectionId) activeConnectionId.value = node.connectionId
  if (node.database) activeDatabase.value = node.database
  if (node.table) selectedTable.value = node.table
  if (node.objectName && ['table', 'view', 'procedure', 'function', 'trigger'].includes(node.type)) {
    selectedTable.value = node.type === 'table' ? node.objectName : selectedTable.value
    setObjectSelection(node.type as ObjectSelection['objectType'], node.objectName)
  }
  if (node.type === 'database') objectSelection.value = null

  if (node.type === 'table' && node.connectionId && node.database && node.table) {
    sqlText.value = `SELECT * FROM ${quoteIdentifier(activeConnectionType.value, node.table)} LIMIT 100;`
    previewTableName.value = node.table
    lastQuerySql.value = sqlText.value
    try {
      queryError.value = ''
      queryResult.value = await api.previewTable(node.connectionId, node.database, node.table)
      applyEditContextFromResult(queryResult.value, node.connectionId, node.database)
      panelState.value.resultPanel = true
    } catch (e) {
      queryError.value = e instanceof Error ? e.message : '预览失败'
      editContext.value = null
    }
  }
}

function applyEditContextFromResult(result: SqlExecuteResponse, connectionId: string, fallbackDatabase?: string) {
  const database = result.sourceDatabase || fallbackDatabase || activeDatabase.value
  const table = result.sourceTable || previewTableName.value

  if (result.editable && table) {
    editContext.value = {
      connectionId,
      database,
      table,
      primaryKeys: result.primaryKeys ?? []
    }
    return
  }

  // Table preview from sidebar: backend should mark editable; keep a safe fallback
  if (previewTableName.value && fallbackDatabase && result.type === 'SELECT') {
    editContext.value = {
      connectionId,
      database: fallbackDatabase,
      table: previewTableName.value,
      primaryKeys: result.primaryKeys ?? []
    }
    return
  }

  editContext.value = null
}

async function refreshQueryResult() {
  if (!activeConnectionId.value) return
  if (previewTableName.value && activeDatabase.value) {
    queryResult.value = await api.previewTable(activeConnectionId.value, activeDatabase.value, previewTableName.value)
    applyEditContextFromResult(queryResult.value, activeConnectionId.value, activeDatabase.value)
    return
  }
  if (lastQuerySql.value) {
    await runSql(lastQuerySql.value, true)
  }
}

function setObjectSelection(objectType: ObjectSelection['objectType'], objectName: string) {
  if (!activeConnectionId.value || !activeDatabase.value) return
  objectSelection.value = { connectionId: activeConnectionId.value, database: activeDatabase.value, objectType, objectName }
  panelState.value.objectDetail = true
}

function openContextMenu(event: MouseEvent, node: TreeNode) {
  contextMenu.value = { visible: true, x: event.clientX, y: event.clientY, node, profileId: '', items: buildContextItems(node) }
}
function openProfileContextMenu(event: MouseEvent, profileId: string) {
  contextMenu.value = {
    visible: true, x: event.clientX, y: event.clientY, node: null, profileId,
    items: [
      { id: 'connect', label: '连接', action: 'connectProfile' },
      { id: 'delete', label: '删除保存的连接', action: 'deleteProfile', danger: true }
    ]
  }
}
function closeContextMenu() { contextMenu.value.visible = false }

function buildContextItems(node: TreeNode): ContextMenuItem[] {
  if (node.type === 'connection') return [{ id: 'dc', label: '断开连接', action: 'disconnect', danger: true }]
  if (node.type === 'database') return [
    { id: 'newt', label: '新建表...', action: 'newTable' },
    { id: 'dropdb', label: '删除数据库...', action: 'dropDatabase', danger: true },
    { id: 'backup', label: '备份数据库', action: 'backupDatabase' }
  ]
  if (node.type === 'table') return [
    { id: 'preview', label: '预览数据', action: 'previewTable' },
    { id: 'ddl', label: '查看 DDL', action: 'designTable' },
    { id: 'query', label: '生成 SELECT 语句', action: 'genSelect' },
    { id: 'd1', label: '', action: '', divider: true },
    { id: 'export', label: '导出 CSV', action: 'exportTableData' },
    { id: 'truncate', label: '清空表', action: 'truncateTable', danger: true },
    { id: 'drop', label: '删除表', action: 'dropTable', danger: true }
  ]
  if (node.type === 'view') return [{ id: 'ddl', label: '查看 DDL', action: 'designTable' }, { id: 'query', label: 'SELECT 预览', action: 'previewView' }]
  return [{ id: 'copy', label: '复制名称', action: 'copyName' }]
}

async function handleContextAction(action: string) {
  const node = contextMenu.value.node
  if (action === 'connectProfile' && contextMenu.value.profileId) return connectProfile(contextMenu.value.profileId)
  if (action === 'deleteProfile' && contextMenu.value.profileId) {
    if (!confirmAction('确认删除保存的连接？')) return
    await api.deleteProfile(contextMenu.value.profileId)
    if (appSettings.value.lastProfileId === contextMenu.value.profileId) {
      appSettings.value.lastProfileId = undefined
      saveSettings(appSettings.value)
    }
    sidebarRef.value?.loadProfiles()
    showToast('success', '已删除')
    return
  }
  if (!node) return
  if (node.connectionId) activeConnectionId.value = node.connectionId
  if (node.database) activeDatabase.value = node.database
  if (node.table || node.objectName) selectedTable.value = node.table || node.objectName || ''

  const actions: Record<string, () => void | Promise<void>> = {
    disconnect: () => handleDisconnect(node.connectionId!),
    newTable: () => { showCreateTable.value = true },
    dropDatabase: () => dropDatabase(),
    backupDatabase: () => backupDatabase(),
    previewTable: () => handleNodeSelect(node),
    designTable: () => { if (node.objectName || node.table) setObjectSelection(node.type === 'view' ? 'view' : 'table', node.objectName || node.table!) },
    genSelect: () => {
      const table = node.table || node.objectName
      if (!table) return
      sqlText.value = `SELECT * FROM ${quoteIdentifier(activeConnectionType.value, table)} LIMIT 100;`
    },
    exportTableData: () => exportTableData(node.table || node.objectName),
    truncateTable: () => truncateTable(node.table || node.objectName),
    dropTable: () => dropTableAction(node.table || node.objectName),
    previewView: () => {
      sqlText.value = `SELECT * FROM ${quoteIdentifier(activeConnectionType.value, node.objectName!)} LIMIT 100;`
      runSql()
    },
    copyName: () => { navigator.clipboard.writeText(node.objectName || node.label); showToast('success', '已复制') }
  }
  await actions[action]?.()
}

async function runSql(customSql?: string, silent = false, mode: 'all' | 'selection' = 'all') {
  const selected = sqlEditorRef.value?.getSelectedText()
  const statements = customSql
    ? resolveSqlStatements(customSql, selected, 'all')
    : resolveSqlStatements(sqlText.value, selected, mode)

  if (!statements.length) {
    if (mode === 'selection') showToast('info', '请先选中要执行的 SQL')
    return
  }
  if (!activeConnectionId.value) { showToast('error', '请先选择连接'); return }

  executing.value = true
  queryError.value = ''
  batchSummary.value = ''
  if (!silent) {
    editContext.value = null
    previewTableName.value = ''
  }
  lastQuerySql.value = statements.join(';\n')
  panelState.value.resultPanel = true
  const start = Date.now()

  try {
    let lastSelectResult: SqlExecuteResponse | null = null
    let lastNonSelectResult: SqlExecuteResponse | null = null
    let totalAffected = 0
    let selectCount = 0

    for (const sql of statements) {
      const result = await api.executeSql(activeConnectionId.value, {
        sql,
        database: activeDatabase.value || undefined,
        limit: appSettings.value.queryLimit
      })
      if (result.type === 'SELECT') {
        selectCount++
        lastSelectResult = result
      } else {
        totalAffected += result.rowCount ?? 0
        lastNonSelectResult = result
      }
    }

    queryResult.value = lastSelectResult ?? lastNonSelectResult
    selectionSubset.value = null
    if (lastSelectResult) {
      resultViewTab.value = 'data'
      applyEditContextFromResult(lastSelectResult, activeConnectionId.value, activeDatabase.value)
    } else {
      editContext.value = null
    }

    if (statements.length > 1) {
      batchSummary.value = `已执行 ${statements.length} 条语句` +
        (totalAffected ? `，影响 ${totalAffected} 行` : '') +
        (selectCount ? `，${selectCount} 条查询返回结果` : '')
      showToast('success', batchSummary.value)
    }

    if (!silent) {
      addQueryHistory({
        sql: lastQuerySql.value,
        database: activeDatabase.value,
        connectionName: activeConnectionName.value,
        executedAt: Date.now(),
        success: true,
        rowCount: queryResult.value?.rowCount,
        durationMs: Date.now() - start
      })
    }
  } catch (e) {
    queryResult.value = null
    editContext.value = null
    queryError.value = e instanceof Error ? e.message : '执行失败'
    if (!silent) {
      addQueryHistory({
        sql: lastQuerySql.value,
        database: activeDatabase.value,
        connectionName: activeConnectionName.value,
        executedAt: Date.now(),
        success: false,
        durationMs: Date.now() - start
      })
    }
  } finally {
    executing.value = false
  }
}

async function explainSql() {
  if (!activeConnectionId.value) { showToast('error', '请先选择连接'); return }
  await runSql(`EXPLAIN ${sqlText.value.replace(/;+\s*$/, '')}`)
  if (isExplainResult(queryResult.value)) {
    resultViewTab.value = 'analytics'
  }
}

function exportCurrentResult() {
  if (!queryResult.value || queryResult.value.type !== 'SELECT') { showToast('error', '没有可导出的查询结果'); return }
  try { exportResultToCsv(queryResult.value); showToast('success', '导出成功') }
  catch (e) { showToast('error', e instanceof Error ? e.message : '导出失败') }
}

async function exportTableData(tableName?: string) {
  const table = tableName || selectedTable.value
  if (!table || !activeConnectionId.value || !activeDatabase.value) return
  try {
    const blob = await api.exportTableCsv(activeConnectionId.value, activeDatabase.value, table)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${table}.csv`
    link.click()
    URL.revokeObjectURL(url)
    showToast('success', '导出成功')
  } catch (e) { showToast('error', e instanceof Error ? e.message : '导出失败') }
}

async function backupDatabase() {
  if (!activeConnectionId.value || !activeDatabase.value) return
  try {
    const { script } = await api.backupScript(activeConnectionId.value, activeDatabase.value)
    downloadText(script, `${activeDatabase.value}-backup.sql`, 'text/sql;charset=utf-8')
    showToast('success', '备份脚本已下载')
  } catch (e) { showToast('error', e instanceof Error ? e.message : '备份失败') }
}

async function dropDatabase() {
  if (!activeConnectionId.value || !activeDatabase.value) return
  if (!confirmAction(`确认删除数据库 ${activeDatabase.value}？此操作不可恢复！`)) return
  await api.dropDatabase(activeConnectionId.value, activeDatabase.value)
  activeDatabase.value = ''
  objectSelection.value = null
  await refreshAll()
  showToast('success', '数据库已删除')
}

async function dropTableAction(tableName?: string) {
  const table = tableName || selectedTable.value
  if (!table || !activeConnectionId.value || !activeDatabase.value) return
  if (!confirmAction(`确认删除表 ${table}？`)) return
  await api.dropTable(activeConnectionId.value, activeDatabase.value, table)
  await refreshAll()
  showToast('success', '表已删除')
}

async function truncateTable(tableName?: string) {
  const table = tableName || selectedTable.value
  if (!table || !activeConnectionId.value || !activeDatabase.value) return
  if (!confirmAction(`确认清空表 ${table}？`)) return
  await api.truncateTable(activeConnectionId.value, activeDatabase.value, table)
  showToast('success', '表已清空')
}

function onImportFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  file.text().then(text => { sqlText.value = text; showToast('success', 'SQL 文件已导入') })
  ;(e.target as HTMLInputElement).value = ''
}

function onDatabaseCreated(name: string) { activeDatabase.value = name; refreshAll() }
function onTableCreated() { refreshAll() }
function onSettingsSaved(settings: AppSettings) {
  appSettings.value = settings
  applyBackgroundTheme(settings)
  showToast('success', '设置已保存')
}

async function onAiSettingsSaved() {
  await loadAiStatus()
  showToast('success', 'AI 配置已保存')
}
function applySqlFromAi(sql: string) {
  sqlText.value = formatAiSql(sql)
  showToast('success', 'AI SQL 已写入编辑器')
}

function applyQuickSql(payload: { sql: string; run: boolean }) {
  const empty = !sqlText.value.trim() || /^--\s*新建查询/m.test(sqlText.value.trim())
  if (sqlEditorRef.value) {
    sqlEditorRef.value.insertText(payload.sql, empty)
  } else {
    sqlText.value = payload.sql
  }
  if (payload.run) {
    void runSql(payload.sql)
    return
  }
  showToast('info', 'SQL 已插入编辑器，Ctrl+Enter 运行')
}

function clampWidth(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, Math.round(value)))
}

function startCenterResize() {
  centerResizeUpperFlex = parseFloat(upperFlex.value) || 1
  centerResizeResultFlex = parseFloat(resultFlex.value) || 1
  centerResizeBodyHeight = workspacePanelRef.value?.panelBodyRef?.clientHeight ?? 600
}

function resizeCenterPanels(delta: number) {
  const totalFlex = centerResizeUpperFlex + centerResizeResultFlex
  const ratio = (delta / centerResizeBodyHeight) * totalFlex
  upperFlex.value = String(Math.max(MIN_CENTER_FLEX, centerResizeUpperFlex + ratio))
  resultFlex.value = String(Math.max(MIN_CENTER_FLEX, centerResizeResultFlex - ratio))
}
</script>

<style scoped>
.app-shell { height: 100%; min-height: 100vh; min-height: 100dvh; display: flex; flex-direction: column; background: var(--bg-primary); }
.workspace { flex: 1; min-height: 0; display: flex; background: transparent; }
.center-panel { flex: 1; min-width: 0; display: flex; flex-direction: column; background: transparent; }
.object-panel, .ai-panel { display: flex; flex-direction: column; background: var(--bg-secondary); flex-shrink: 0; min-width: 0; }
.panel-title-bar { height: 28px; display: flex; align-items: center; justify-content: space-between; padding: 0 10px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border); font-size: 11px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.04em; flex-shrink: 0; }
.panel-action { background: transparent; border: none; color: var(--text-muted); font-size: 14px; }
.panel-content { flex: 1; min-height: 0; overflow: auto; }
.ai-content { padding: 0; }
.center-upper {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-section, .result-section { min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.editor-section { flex: 1; min-height: 0; }
.result-section { min-height: 120px; border-top: 1px solid var(--border); }
.result-header { display: flex; align-items: center; justify-content: space-between; height: 28px; padding: 0 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border); font-size: 11px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; flex-shrink: 0; }
.result-header-left { display: flex; align-items: center; gap: 10px; }
.result-view-tabs { display: flex; gap: 2px; text-transform: none; font-weight: 500; }
.result-tab { border: 1px solid transparent; background: transparent; color: var(--text-muted); padding: 2px 8px; border-radius: var(--radius); font-size: 11px; cursor: pointer; }
.result-tab.active { color: var(--accent); background: var(--bg-primary); border-color: var(--border); }
.result-meta { font-weight: 400; text-transform: none; color: var(--text-muted); }
.toast { position: fixed; right: 16px; bottom: calc(var(--statusbar-height) + 12px); padding: 10px 16px; border-radius: var(--radius); box-shadow: var(--shadow-md); z-index: 4000; font-size: 13px; border: 1px solid var(--border); }
.toast.success { background: var(--success-bg); border-color: #a8e6b4; color: #1a7f37; }
.toast.error { background: var(--danger-bg); border-color: #ffc9c5; color: var(--danger); }
.toast.info { background: var(--accent-light); border-color: #b3ccff; color: var(--accent); }
.shortcut-table { width: 100%; border-collapse: collapse; }
.shortcut-table td { padding: 6px 0; border-bottom: 1px solid var(--border); }
.shortcut-table td:last-child { text-align: right; }
kbd { background: var(--bg-tertiary); border: 1px solid var(--border-strong); border-radius: 3px; padding: 2px 6px; font-size: 11px; }
</style>
