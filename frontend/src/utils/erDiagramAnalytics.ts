import type { ErDiagramData } from '@/types/features'
import type { SchemaInsight } from '@/utils/schemaAnalytics'

export interface ErDiagramAnalytics {
  tableCount: number
  relationshipCount: number
  avgColumnsPerTable: number
  maxColumnsTable: { name: string; count: number } | null
  orphanTables: string[]
  hubTables: Array<{ name: string; inCount: number; outCount: number; total: number }>
  tablesWithoutPk: string[]
  isolatedClusters: number
  insights: SchemaInsight[]
}

export function analyzeErDiagram(data: ErDiagramData | null): ErDiagramAnalytics | null {
  if (!data?.tables.length) return null

  const tableNames = data.tables.map(t => t.name)
  const inCount = new Map<string, number>()
  const outCount = new Map<string, number>()
  for (const name of tableNames) {
    inCount.set(name, 0)
    outCount.set(name, 0)
  }
  for (const rel of data.relationships) {
    outCount.set(rel.fromTable, (outCount.get(rel.fromTable) ?? 0) + 1)
    inCount.set(rel.toTable, (inCount.get(rel.toTable) ?? 0) + 1)
  }

  const orphanTables = tableNames.filter(name => (inCount.get(name) ?? 0) + (outCount.get(name) ?? 0) === 0)
  const hubTables = tableNames
    .map(name => ({
      name,
      inCount: inCount.get(name) ?? 0,
      outCount: outCount.get(name) ?? 0,
      total: (inCount.get(name) ?? 0) + (outCount.get(name) ?? 0)
    }))
    .filter(t => t.total > 0)
    .sort((a, b) => b.total - a.total)
    .slice(0, 8)

  const tablesWithoutPk = data.tables
    .filter(t => !t.columns.some(c => c.key === 'PRI'))
    .map(t => t.name)

  const colCounts = data.tables.map(t => ({ name: t.name, count: t.columns.length }))
  const maxCol = colCounts.sort((a, b) => b.count - a.count)[0] ?? null
  const avgColumnsPerTable = data.tables.reduce((s, t) => s + t.columns.length, 0) / data.tables.length

  const insights: SchemaInsight[] = []
  if (orphanTables.length) {
    insights.push({
      level: 'info',
      title: '孤立表',
      detail: `${orphanTables.length} 张表无外键关联：${orphanTables.slice(0, 5).join(', ')}${orphanTables.length > 5 ? '…' : ''}`
    })
  }
  if (tablesWithoutPk.length) {
    insights.push({
      level: 'warn',
      title: '无主键表',
      detail: `${tablesWithoutPk.length} 张表未检测到主键列。`
    })
  }
  if (hubTables[0]?.total >= 4) {
    insights.push({
      level: 'info',
      title: '核心枢纽表',
      detail: `${hubTables[0].name} 关联 ${hubTables[0].total} 条外键，是 schema 的中心节点。`
    })
  }
  if (data.relationships.length === 0 && data.tables.length > 1) {
    insights.push({
      level: 'warn',
      title: '缺少外键关系',
      detail: '未检测到外键，可能是逻辑关联或未声明 CONSTRAINT。'
    })
  }

  const connected = new Set<string>()
  for (const rel of data.relationships) {
    connected.add(rel.fromTable)
    connected.add(rel.toTable)
  }
  const isolatedClusters = tableNames.filter(n => !connected.has(n)).length

  return {
    tableCount: data.tables.length,
    relationshipCount: data.relationships.length,
    avgColumnsPerTable,
    maxColumnsTable: maxCol,
    orphanTables,
    hubTables,
    tablesWithoutPk,
    isolatedClusters,
    insights
  }
}
