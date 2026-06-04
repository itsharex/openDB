import type { ColumnInfo, IndexInfo } from '@/types'

export interface SchemaInsight {
  level: 'good' | 'info' | 'warn'
  title: string
  detail: string
}

export interface TableSchemaAnalytics {
  columnCount: number
  indexCount: number
  uniqueIndexCount: number
  primaryKeyColumns: string[]
  nullableColumnCount: number
  nullableRate: number
  hasPrimaryKey: boolean
  indexedColumnCount: number
  unindexedColumns: string[]
  textHeavyColumns: string[]
  numericColumns: string[]
  wideTable: boolean
  healthScore: number
  insights: SchemaInsight[]
}

function isTextType(type: string) {
  const t = type.toLowerCase()
  return t.includes('char') || t.includes('text') || t.includes('blob') || t.includes('json')
}

function isNumericType(type: string) {
  const t = type.toLowerCase()
  return t.includes('int') || t.includes('decimal') || t.includes('numeric') || t.includes('float') || t.includes('double') || t.includes('real')
}

function isNullable(nullable?: string) {
  return (nullable ?? 'YES').toUpperCase() === 'YES'
}

export function analyzeTableSchema(columns: ColumnInfo[], indexes: IndexInfo[]): TableSchemaAnalytics {
  const primaryKeyColumns = columns.filter(c => c.key === 'PRI').map(c => c.name)
  const indexedNames = new Set(indexes.map(i => i.columnName))
  const nullableColumnCount = columns.filter(c => isNullable(c.nullable)).length
  const nullableRate = columns.length ? (nullableColumnCount / columns.length) * 100 : 0
  const unindexedColumns = columns
    .filter(c => c.key !== 'PRI' && !indexedNames.has(c.name))
    .map(c => c.name)
  const textHeavyColumns = columns.filter(c => isTextType(c.type)).map(c => c.name)
  const numericColumns = columns.filter(c => isNumericType(c.type)).map(c => c.name)
  const uniqueIndexCount = new Set(indexes.filter(i => i.unique).map(i => i.name)).size

  const insights: SchemaInsight[] = []
  const hasPrimaryKey = primaryKeyColumns.length > 0

  if (!hasPrimaryKey) {
    insights.push({
      level: 'warn',
      title: '缺少主键',
      detail: '建议为表添加主键，便于关联、复制与 ORM 映射，并提升 InnoDB 聚簇索引效率。'
    })
  } else {
    insights.push({
      level: 'good',
      title: '已定义主键',
      detail: `主键列: ${primaryKeyColumns.join(', ')}`
    })
  }

  if (nullableRate > 50) {
    insights.push({
      level: 'warn',
      title: '可空列占比偏高',
      detail: `${nullableRate.toFixed(0)}% 的列允许 NULL，可能增加查询与索引复杂度。`
    })
  }

  if (unindexedColumns.length > 0 && columns.length >= 6) {
    const sample = unindexedColumns.slice(0, 4).join(', ')
    insights.push({
      level: 'info',
      title: '可能存在未索引列',
      detail: `${unindexedColumns.length} 列无索引覆盖（如 ${sample}${unindexedColumns.length > 4 ? '…' : ''}），若常用于 WHERE/JOIN 可考虑加索引。`
    })
  }

  if (textHeavyColumns.length >= 3) {
    insights.push({
      level: 'info',
      title: '大文本列较多',
      detail: `${textHeavyColumns.length} 个 TEXT/VARCHAR 类列，注意行宽与缓存效率。`
    })
  }

  if (columns.length >= 30) {
    insights.push({
      level: 'info',
      title: '宽表结构',
      detail: `共 ${columns.length} 列，建议评估是否可垂直拆分或归档历史字段。`
    })
  }

  if (indexes.length === 0 && columns.length > 3) {
    insights.push({
      level: 'warn',
      title: '无二级索引',
      detail: '除主键外没有其他索引，复杂查询可能全表扫描。'
    })
  }

  const duplicateIndexCandidates = indexes.filter((idx, _, all) =>
    all.some(other => other !== idx && other.columnName === idx.columnName && other.name !== idx.name)
  )
  if (duplicateIndexCandidates.length) {
    insights.push({
      level: 'info',
      title: '重复索引候选',
      detail: '同一列存在多个索引，可考虑合并以减少写入开销。'
    })
  }

  let healthScore = 100
  if (!hasPrimaryKey) healthScore -= 25
  if (nullableRate > 60) healthScore -= 10
  if (indexes.length === 0 && columns.length > 3) healthScore -= 15
  if (columns.length >= 40) healthScore -= 10
  healthScore = Math.max(0, Math.min(100, healthScore))

  return {
    columnCount: columns.length,
    indexCount: indexes.length,
    uniqueIndexCount,
    primaryKeyColumns,
    nullableColumnCount,
    nullableRate,
    hasPrimaryKey,
    indexedColumnCount: indexedNames.size,
    unindexedColumns,
    textHeavyColumns,
    numericColumns,
    wideTable: columns.length >= 30,
    healthScore,
    insights
  }
}

export function healthScoreLabel(score: number) {
  if (score >= 85) return '优秀'
  if (score >= 70) return '良好'
  if (score >= 50) return '一般'
  return '需改进'
}
