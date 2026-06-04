export interface MenuItem {
  id: string
  label: string
  shortcut?: string
  divider?: boolean
  disabled?: boolean
  checked?: boolean
  action?: string
  children?: MenuItem[]
}

export interface MenuGroup {
  id: string
  label: string
  items: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
  {
    id: 'file',
    label: '文件',
    items: [
      { id: 'new-connection', label: '新建连接', shortcut: 'Ctrl+N', action: 'newConnection' },
      { id: 'manage-profiles', label: '连接管理...', action: 'manageProfiles' },
      { id: 'disconnect', label: '断开当前连接', action: 'disconnect' },
      { id: 'refresh', label: '刷新', shortcut: 'F5', action: 'refreshConnections' },
      { id: 'd1', label: '', divider: true },
      { id: 'import-sql', label: '导入 SQL 文件...', action: 'importSql' },
      { id: 'export-result', label: '导出查询结果 (CSV)...', action: 'exportResult' },
      { id: 'd2', label: '', divider: true },
      { id: 'exit', label: '退出', shortcut: 'Ctrl+Q', action: 'exitApp' }
    ]
  },
  {
    id: 'edit',
    label: '编辑',
    items: [
      { id: 'undo', label: '撤销', shortcut: 'Ctrl+Z', action: 'editorUndo' },
      { id: 'redo', label: '重做', shortcut: 'Ctrl+Y', action: 'editorRedo' },
      { id: 'd1', label: '', divider: true },
      { id: 'copy', label: '复制', shortcut: 'Ctrl+C', action: 'copySql' },
      { id: 'select-all', label: '全选', shortcut: 'Ctrl+A', action: 'selectAll' },
      { id: 'd2', label: '', divider: true },
      { id: 'find', label: '查找和替换...', shortcut: 'Ctrl+F', action: 'findReplace' },
      { id: 'format', label: '格式化 SQL', shortcut: 'Ctrl+Shift+F', action: 'formatSql' }
    ]
  },
  {
    id: 'view',
    label: '查看',
    items: [
      { id: 'object-browser', label: '对象浏览器', action: 'toggleObjectBrowser', checked: true },
      { id: 'object-detail', label: '对象详情面板', action: 'toggleObjectDetail', checked: true },
      { id: 'result-panel', label: '结果面板', action: 'toggleResultPanel', checked: true },
      { id: 'ai-panel', label: 'AI 助手面板', action: 'toggleAiPanel', checked: true },
      { id: 'd1', label: '', divider: true },
      { id: 'er-diagram', label: 'ER 图...', action: 'erDiagram' },
      { id: 'refresh-schema', label: '刷新数据库结构', action: 'refreshSchema' },
      { id: 'fullscreen', label: '全屏模式', shortcut: 'F11', action: 'fullscreen' }
    ]
  },
  {
    id: 'query',
    label: '查询',
    items: [
      { id: 'new-query', label: '新建查询', shortcut: 'Ctrl+T', action: 'newQuery' },
      { id: 'run', label: '运行', shortcut: 'Ctrl+Enter', action: 'runSql' },
      { id: 'run-selected', label: '运行选中语句', shortcut: 'Ctrl+Shift+Enter', action: 'runSelected' },
      { id: 'd1', label: '', divider: true },
      { id: 'explain', label: '执行计划 (EXPLAIN)', action: 'explainSql' },
      { id: 'history', label: '查询历史', action: 'queryHistory' }
    ]
  },
  {
    id: 'database',
    label: '数据库',
    items: [
      { id: 'new-db', label: '新建数据库...', action: 'newDatabase' },
      { id: 'drop-db', label: '删除数据库...', action: 'dropDatabase' },
      { id: 'new-table', label: '新建表 (DDL)...', action: 'newTable' },
      { id: 'table-designer', label: '可视化建表设计器...', action: 'tableDesigner' },
      { id: 'design-table', label: '查看表结构/DDL', action: 'designTable' },
      { id: 'd1', label: '', divider: true },
      { id: 'import-data', label: '导入数据 (CSV)...', action: 'importData' },
      { id: 'export-data', label: '导出表数据 (CSV)...', action: 'exportTableData' },
      { id: 'truncate-table', label: '清空表...', action: 'truncateTable' },
      { id: 'drop-table', label: '删除表...', action: 'dropTable' },
      { id: 'd2', label: '', divider: true },
      { id: 'backup', label: '备份数据库 (SQL脚本)...', action: 'backupDatabase' }
    ]
  },
  {
    id: 'tools',
    label: '工具',
    items: [
      { id: 'transfer', label: '数据传输...', action: 'dataTransfer' },
      { id: 'sync', label: '结构同步...', action: 'schemaSync' },
      { id: 'compare', label: '数据对比...', action: 'dataCompare' },
      { id: 'd1', label: '', divider: true },
      { id: 'options', label: '选项...', action: 'options' },
      { id: 'plugins', label: '插件管理...', action: 'pluginManager' }
    ]
  },
  {
    id: 'ai',
    label: 'AI',
    items: [
      { id: 'ai-panel', label: '打开 AI 助手', action: 'toggleAiPanel' },
      { id: 'ai-generate', label: 'AI 生成 SQL', action: 'aiGenerate' },
      { id: 'ai-explain', label: 'AI 解释 SQL', action: 'aiExplain' },
      { id: 'ai-optimize', label: 'AI 优化 SQL', action: 'aiOptimize' },
      { id: 'd1', label: '', divider: true },
      { id: 'ai-settings', label: 'AI 设置...', action: 'aiSettings' }
    ]
  },
  {
    id: 'window',
    label: '窗口',
    items: [
      { id: 'reset-layout', label: '重置布局', action: 'resetLayout' },
      { id: 'horizontal-split', label: '显示/隐藏结果面板', action: 'toggleResultPanel' },
      { id: 'vertical-split', label: '显示/隐藏 AI 面板', action: 'toggleAiPanel' }
    ]
  },
  {
    id: 'help',
    label: '帮助',
    items: [
      { id: 'docs', label: '使用文档', action: 'showDocs' },
      { id: 'shortcuts', label: '快捷键参考', action: 'showShortcuts' },
      { id: 'd1', label: '', divider: true },
      { id: 'about', label: '关于 openDB', action: 'showAbout' }
    ]
  }
]

export interface ToolbarItem {
  id: string
  label: string
  icon: string
  action: string
  disabled?: boolean
  primary?: boolean
}

export const toolbarItems: ToolbarItem[] = [
  { id: 'new-connection', label: '连接', icon: '＋', action: 'newConnection' },
  { id: 'refresh', label: '刷新', icon: '↻', action: 'refreshConnections' },
  { id: 'sep1', label: '', icon: '', action: 'separator' },
  { id: 'new-query', label: '查询', icon: '📝', action: 'newQuery' },
  { id: 'run', label: '运行', icon: '▶', action: 'runSql', primary: true },
  { id: 'sep2', label: '', icon: '', action: 'separator' },
  { id: 'format', label: '格式化', icon: '≡', action: 'formatSql' },
  { id: 'explain', label: '计划', icon: '⚡', action: 'explainSql' },
  { id: 'designer', label: '设计', icon: '⬡', action: 'tableDesigner' },
  { id: 'er', label: 'ER', icon: '◫', action: 'erDiagram' },
  { id: 'history', label: '历史', icon: '🕐', action: 'queryHistory' },
  { id: 'sep3', label: '', icon: '', action: 'separator' },
  { id: 'export', label: '导出', icon: '⬇', action: 'exportResult' },
  { id: 'ai', label: 'AI', icon: '✦', action: 'toggleAiPanel' }
]
