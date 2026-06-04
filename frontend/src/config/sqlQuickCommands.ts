export interface SqlQuickCommand {
  id: string
  label: string
  title: string
  sql: string
  requiresTable?: boolean
  requiresDatabase?: boolean
}

export const sqlQuickCommandGroups: Array<{ id: string; label: string; commands: SqlQuickCommand[] }> = [
  {
    id: 'query',
    label: '查询',
    commands: [
      {
        id: 'select-all',
        label: 'SELECT *',
        title: '查询表数据（LIMIT 100）',
        sql: 'SELECT * FROM `{table}` LIMIT 100;',
        requiresTable: true
      },
      {
        id: 'count',
        label: 'COUNT',
        title: '统计行数',
        sql: 'SELECT COUNT(*) AS total FROM `{table}`;',
        requiresTable: true
      },
      {
        id: 'top10',
        label: 'TOP 10',
        title: '查询前 10 行',
        sql: 'SELECT * FROM `{table}` LIMIT 10;',
        requiresTable: true
      }
    ]
  },
  {
    id: 'structure',
    label: '结构',
    commands: [
      {
        id: 'describe',
        label: 'DESCRIBE',
        title: '查看表结构',
        sql: 'DESCRIBE `{table}`;',
        requiresTable: true
      },
      {
        id: 'show-create',
        label: 'SHOW CREATE',
        title: '查看建表语句',
        sql: 'SHOW CREATE TABLE `{table}`;',
        requiresTable: true
      },
      {
        id: 'show-index',
        label: 'SHOW INDEX',
        title: '查看索引',
        sql: 'SHOW INDEX FROM `{table}`;',
        requiresTable: true
      },
      {
        id: 'show-columns',
        label: 'SHOW COLUMNS',
        title: '查看列信息',
        sql: 'SHOW COLUMNS FROM `{table}`;',
        requiresTable: true
      }
    ]
  },
  {
    id: 'meta',
    label: '元数据',
    commands: [
      {
        id: 'show-tables',
        label: 'SHOW TABLES',
        title: '当前库所有表',
        sql: 'SHOW TABLES;',
        requiresDatabase: true
      },
      {
        id: 'show-databases',
        label: 'SHOW DATABASES',
        title: '所有数据库',
        sql: 'SHOW DATABASES;'
      },
      {
        id: 'show-processlist',
        label: 'PROCESSLIST',
        title: '连接与进程',
        sql: 'SHOW PROCESSLIST;'
      },
      {
        id: 'show-variables',
        label: 'VARIABLES',
        title: '服务器变量',
        sql: "SHOW VARIABLES LIKE '%version%';"
      }
    ]
  },
  {
    id: 'tools',
    label: '工具',
    commands: [
      {
        id: 'explain',
        label: 'EXPLAIN',
        title: '执行计划（基于当前 SQL）',
        sql: 'EXPLAIN SELECT * FROM `{table}` LIMIT 10;',
        requiresTable: true
      },
      {
        id: 'truncate-template',
        label: 'TRUNCATE',
        title: '清空表模板（需手动确认后运行）',
        sql: '-- 危险操作，确认后删除注释再运行\n-- TRUNCATE TABLE `{table}`;',
        requiresTable: true
      }
    ]
  }
]

export function resolveQuickSql(
  template: string,
  context: { database?: string; table?: string }
): string {
  const table = context.table || 'your_table'
  const database = context.database || 'your_database'
  return template.replace(/\{table\}/g, table).replace(/\{database\}/g, database)
}

export const editorShortcutHints = [
  { keys: 'Ctrl+Enter', label: '运行全部/选中' },
  { keys: 'Ctrl+Shift+Enter', label: '仅运行选中' },
  { keys: '左侧▶', label: '快捷运行' },
  { keys: 'Ctrl+Space', label: '补全' },
  { keys: 'Ctrl+F', label: '查找' },
  { keys: 'Ctrl+Shift+F', label: '格式化' },
  { keys: 'F5', label: '刷新' },
  { keys: 'Shift+点击', label: '快捷命令直接运行' }
]
