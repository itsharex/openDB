import type { DesignerColumn } from '@/types/features'

export interface DesignerAnalytics {
  columnCount: number
  primaryKeyCount: number
  nullablePk: boolean
  hasAutoIncrement: boolean
  unnamedColumns: number
  duplicateNames: string[]
  healthScore: number
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

export function analyzeDesigner(tableName: string, columns: DesignerColumn[]): DesignerAnalytics {
  const named = columns.filter(c => c.name.trim())
  const names = named.map(c => c.name.trim().toLowerCase())
  const duplicateNames = [...new Set(names.filter((n, i) => names.indexOf(n) !== i))]
  const primaryKeyCount = named.filter(c => c.primaryKey).length
  const nullablePk = named.some(c => c.primaryKey && c.nullable)
  const hasAutoIncrement = named.some(c => c.autoIncrement)
  const unnamedColumns = columns.length - named.length

  const insights: DesignerAnalytics['insights'] = []
  if (!tableName.trim()) {
    insights.push({ level: 'warn', title: '缺少表名', detail: '请填写有效的表名。' })
  }
  if (primaryKeyCount === 0) {
    insights.push({ level: 'warn', title: '建议添加主键', detail: '无主键的表不便于 ORM 和数据编辑。' })
  } else {
    insights.push({ level: 'good', title: '主键已定义', detail: `${primaryKeyCount} 个主键列。` })
  }
  if (nullablePk) {
    insights.push({ level: 'warn', title: '主键不应可空', detail: '主键列已自动设为非空，请检查设计。' })
  }
  if (duplicateNames.length) {
    insights.push({ level: 'warn', title: '重复字段名', detail: duplicateNames.join(', ') })
  }
  if (unnamedColumns > 0) {
    insights.push({ level: 'info', title: '未命名字段', detail: `${unnamedColumns} 个字段尚未命名，不会写入 DDL。` })
  }
  if (named.length >= 25) {
    insights.push({ level: 'info', title: '字段较多', detail: '宽表可能影响查询性能，可考虑拆分。' })
  }

  let healthScore = 100
  if (!tableName.trim()) healthScore -= 20
  if (primaryKeyCount === 0) healthScore -= 25
  if (duplicateNames.length) healthScore -= 20
  if (nullablePk) healthScore -= 10
  healthScore = Math.max(0, Math.min(100, healthScore))

  return {
    columnCount: named.length,
    primaryKeyCount,
    nullablePk,
    hasAutoIncrement,
    unnamedColumns,
    duplicateNames,
    healthScore,
    insights
  }
}

export interface DdlAnalytics {
  hasCreateTable: boolean
  tableName: string | null
  columnCount: number
  hasPrimaryKey: boolean
  hasEngine: boolean
  hasCharset: boolean
  healthScore: number
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

export function analyzeDdl(ddl: string): DdlAnalytics {
  const text = ddl.trim()
  const upper = text.toUpperCase()
  const hasCreateTable = upper.startsWith('CREATE TABLE')
  const tableMatch = text.match(/CREATE\s+TABLE\s+[`"']?(\w+)[`"']?/i)
  const tableName = tableMatch?.[1] ?? null
  const columnLines = text.match(/^\s*[`"']?\w+[`"']?\s+\w+/gm) ?? []
  const hasPrimaryKey = /PRIMARY\s+KEY/i.test(text)
  const hasEngine = /ENGINE\s*=/i.test(text)
  const hasCharset = /CHARSET\s*=/i.test(text) || /CHARACTER\s+SET/i.test(text)

  const insights: DdlAnalytics['insights'] = []
  if (!hasCreateTable) {
    insights.push({ level: 'warn', title: '非 CREATE TABLE', detail: '语句应以 CREATE TABLE 开头。' })
  }
  if (!hasPrimaryKey) {
    insights.push({ level: 'warn', title: '未检测到主键', detail: '建议添加 PRIMARY KEY 约束。' })
  } else {
    insights.push({ level: 'good', title: '包含主键', detail: '已定义 PRIMARY KEY。' })
  }
  if (!hasEngine) {
    insights.push({ level: 'info', title: '未指定引擎', detail: 'MySQL 将使用默认存储引擎。' })
  }
  if (!hasCharset) {
    insights.push({ level: 'info', title: '未指定字符集', detail: '建议显式设置 utf8mb4。' })
  }

  let healthScore = 100
  if (!hasCreateTable) healthScore -= 30
  if (!hasPrimaryKey) healthScore -= 25
  if (!tableName) healthScore -= 15
  healthScore = Math.max(0, Math.min(100, healthScore))

  return {
    hasCreateTable,
    tableName,
    columnCount: columnLines.length,
    hasPrimaryKey,
    hasEngine,
    hasCharset,
    healthScore,
    insights
  }
}
