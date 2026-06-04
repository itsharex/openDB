import type { SqlExecuteResponse } from '@/types'

export type ColumnKind = 'number' | 'integer' | 'boolean' | 'date' | 'datetime' | 'string' | 'empty' | 'mixed'

export interface ValueFrequency {
  value: string
  count: number
  pct: number
}

export interface HistogramBin {
  label: string
  count: number
  pct: number
}

export interface ColumnProfile {
  name: string
  kind: ColumnKind
  count: number
  nullCount: number
  nullRate: number
  distinctCount: number
  uniqueRate: number
  min?: number
  max?: number
  sum?: number
  mean?: number
  median?: number
  stdDev?: number
  q1?: number
  q3?: number
  minLength?: number
  maxLength?: number
  avgLength?: number
  topValues: ValueFrequency[]
  histogram: HistogramBin[]
  outlierCount: number
}

export interface DataInsight {
  level: 'good' | 'info' | 'warn'
  title: string
  detail: string
}

export interface CorrelationPair {
  colA: string
  colB: string
  coefficient: number
}

export interface GroupAggregateRow {
  groupValue: string
  count: number
  metrics: Record<string, { sum: number; avg: number; min: number; max: number }>
}

export interface ResultAnalytics {
  rowCount: number
  columnCount: number
  numericColumnCount: number
  stringColumnCount: number
  duplicateRowCount: number
  duplicateRate: number
  completeRowCount: number
  completeRowRate: number
  columns: ColumnProfile[]
  insights: DataInsight[]
  correlations: CorrelationPair[]
}

function isNullish(value: unknown) {
  return value == null || value === ''
}

function toNumber(value: unknown): number | null {
  if (isNullish(value)) return null
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'boolean') return value ? 1 : 0
  const text = String(value).trim()
  if (!text) return null
  const n = Number(text)
  return Number.isFinite(n) ? n : null
}

function percentile(sorted: number[], p: number) {
  if (!sorted.length) return 0
  const index = (sorted.length - 1) * p
  const lower = Math.floor(index)
  const upper = Math.ceil(index)
  if (lower === upper) return sorted[lower]
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
}

function stdDev(values: number[], mean: number) {
  if (values.length <= 1) return 0
  const variance = values.reduce((acc, v) => acc + (v - mean) ** 2, 0) / (values.length - 1)
  return Math.sqrt(variance)
}

function inferKind(values: unknown[]): ColumnKind {
  const nonNull = values.filter(v => !isNullish(v))
  if (!nonNull.length) return 'empty'

  let numeric = 0
  let integer = 0
  let boolean = 0
  let date = 0
  let string = 0

  for (const value of nonNull) {
    if (typeof value === 'boolean') {
      boolean++
      continue
    }
    const n = toNumber(value)
    if (n != null) {
      numeric++
      if (Number.isInteger(n)) integer++
      continue
    }
    const text = String(value).trim()
    const parsed = Date.parse(text)
    if (!Number.isNaN(parsed) && /^\d{4}-\d{2}-\d{2}/.test(text)) {
      date++
      continue
    }
    string++
  }

  const total = nonNull.length
  if (boolean / total > 0.8) return 'boolean'
  if (date / total > 0.8) return 'date'
  if (numeric / total > 0.8) return integer / total > 0.95 ? 'integer' : 'number'
  if (string / total > 0.8) return 'string'
  return 'mixed'
}

function buildHistogram(values: number[], bins = 8): HistogramBin[] {
  if (!values.length) return []
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (min === max) {
    return [{ label: formatNumber(min), count: values.length, pct: 100 }]
  }

  const width = (max - min) / bins
  const counts = Array.from({ length: bins }, () => 0)
  for (const value of values) {
    let index = Math.floor((value - min) / width)
    if (index >= bins) index = bins - 1
    counts[index]++
  }

  return counts.map((count, index) => {
    const start = min + width * index
    const end = index === bins - 1 ? max : min + width * (index + 1)
    return {
      label: `${formatNumber(start)} ~ ${formatNumber(end)}`,
      count,
      pct: (count / values.length) * 100
    }
  })
}

function formatNumber(value: number) {
  if (Math.abs(value) >= 1000 || Math.abs(value) < 0.01) {
    return value.toPrecision(4)
  }
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

function topFrequencies(values: unknown[], limit = 8): ValueFrequency[] {
  const counter = new Map<string, number>()
  let total = 0
  for (const value of values) {
    if (isNullish(value)) continue
    const key = typeof value === 'object' ? JSON.stringify(value) : String(value)
    counter.set(key, (counter.get(key) ?? 0) + 1)
    total++
  }
  return [...counter.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([value, count]) => ({
      value,
      count,
      pct: total ? (count / total) * 100 : 0
    }))
}

function pearson(a: number[], b: number[]) {
  const n = Math.min(a.length, b.length)
  if (n < 3) return 0
  const meanA = a.reduce((s, v) => s + v, 0) / n
  const meanB = b.reduce((s, v) => s + v, 0) / n
  let num = 0
  let denA = 0
  let denB = 0
  for (let i = 0; i < n; i++) {
    const da = a[i] - meanA
    const db = b[i] - meanB
    num += da * db
    denA += da * da
    denB += db * db
  }
  if (!denA || !denB) return 0
  return num / Math.sqrt(denA * denB)
}

function duplicateStats(rows: Record<string, unknown>[]) {
  const seen = new Map<string, number>()
  for (const row of rows) {
    const key = JSON.stringify(row)
    seen.set(key, (seen.get(key) ?? 0) + 1)
  }
  let duplicateRows = 0
  for (const count of seen.values()) {
    if (count > 1) duplicateRows += count
  }
  return {
    duplicateRowCount: duplicateRows,
    duplicateRate: rows.length ? (duplicateRows / rows.length) * 100 : 0
  }
}

function buildInsights(columns: ColumnProfile[], duplicateRate: number, rowCount: number): DataInsight[] {
  const insights: DataInsight[] = []

  if (duplicateRate > 0) {
    insights.push({
      level: duplicateRate > 10 ? 'warn' : 'info',
      title: '重复行',
      detail: `约 ${duplicateRate.toFixed(1)}% 的行完全重复，可能需要 DISTINCT 或去重处理。`
    })
  }

  for (const col of columns) {
    if (col.nullRate >= 30) {
      insights.push({
        level: 'warn',
        title: `${col.name} 空值偏高`,
        detail: `空值占比 ${col.nullRate.toFixed(1)}%，建议检查数据来源或补全策略。`
      })
    } else if (col.nullRate > 0 && col.nullRate < 5) {
      insights.push({
        level: 'good',
        title: `${col.name} 完整性较好`,
        detail: `空值仅占 ${col.nullRate.toFixed(1)}%。`
      })
    }

    if (col.distinctCount === 1 && col.count > 1) {
      insights.push({
        level: 'info',
        title: `${col.name} 为常量列`,
        detail: '所有非空值相同，可考虑从 SELECT 中移除以简化结果。'
      })
    }

    if (col.uniqueRate >= 98 && col.kind === 'integer' && rowCount >= 20) {
      insights.push({
        level: 'info',
        title: `${col.name} 疑似主键/ID`,
        detail: `唯一率 ${col.uniqueRate.toFixed(1)}%，接近唯一标识符。`
      })
    }

    if (col.outlierCount > 0 && col.kind === 'number' || col.kind === 'integer') {
      insights.push({
        level: col.outlierCount / col.count > 0.05 ? 'warn' : 'info',
        title: `${col.name} 存在离群值`,
        detail: `IQR 规则检测到 ${col.outlierCount} 个潜在离群点（${((col.outlierCount / col.count) * 100).toFixed(1)}%）。`
      })
    }

    if (col.mean != null && col.median != null && col.stdDev != null && col.stdDev > 0) {
      const skew = Math.abs(col.mean - col.median) / col.stdDev
      if (skew > 1.2) {
        insights.push({
          level: 'info',
          title: `${col.name} 分布偏斜`,
          detail: `均值 ${formatNumber(col.mean)}，中位数 ${formatNumber(col.median)}，可能存在长尾分布。`
        })
      }
    }
  }

  return insights.slice(0, 12)
}

export function analyzeResult(result: SqlExecuteResponse | null): ResultAnalytics | null {
  if (!result || result.type !== 'SELECT' || !result.columns?.length) return null

  const rows = result.rows ?? []
  const rowCount = rows.length
  const columns: ColumnProfile[] = result.columns.map(name => {
    const values = rows.map(row => row[name])
    const nonNullValues = values.filter(v => !isNullish(v))
    const count = nonNullValues.length
    const nullCount = rowCount - count
    const distinct = new Set(nonNullValues.map(v => (typeof v === 'object' ? JSON.stringify(v) : String(v))))
    const kind = inferKind(values)
    const numericValues = nonNullValues.map(toNumber).filter((v): v is number => v != null)
    const sorted = [...numericValues].sort((a, b) => a - b)
    const mean = sorted.length ? sorted.reduce((s, v) => s + v, 0) / sorted.length : undefined
    const q1 = sorted.length ? percentile(sorted, 0.25) : undefined
    const q3 = sorted.length ? percentile(sorted, 0.75) : undefined
    let outlierCount = 0
    if (sorted.length >= 4 && q1 != null && q3 != null) {
      const iqr = q3 - q1
      const lower = q1 - 1.5 * iqr
      const upper = q3 + 1.5 * iqr
      outlierCount = sorted.filter(v => v < lower || v > upper).length
    }

    const lengths = nonNullValues.map(v => String(v).length)
    const avgLength = lengths.length
      ? lengths.reduce((s, v) => s + v, 0) / lengths.length
      : undefined

    return {
      name,
      kind,
      count,
      nullCount,
      nullRate: rowCount ? (nullCount / rowCount) * 100 : 0,
      distinctCount: distinct.size,
      uniqueRate: count ? (distinct.size / count) * 100 : 0,
      min: sorted.length ? sorted[0] : undefined,
      max: sorted.length ? sorted[sorted.length - 1] : undefined,
      sum: sorted.length ? sorted.reduce((s, v) => s + v, 0) : undefined,
      mean,
      median: sorted.length ? percentile(sorted, 0.5) : undefined,
      stdDev: mean != null ? stdDev(sorted, mean) : undefined,
      q1,
      q3,
      minLength: lengths.length ? Math.min(...lengths) : undefined,
      maxLength: lengths.length ? Math.max(...lengths) : undefined,
      avgLength,
      topValues: topFrequencies(values),
      histogram: buildHistogram(sorted),
      outlierCount
    }
  })

  const numericColumns = columns.filter(c => c.kind === 'number' || c.kind === 'integer')
  const correlations: CorrelationPair[] = []
  for (let i = 0; i < numericColumns.length; i++) {
    for (let j = i + 1; j < numericColumns.length; j++) {
      const colA = numericColumns[i].name
      const colB = numericColumns[j].name
      const pairsA: number[] = []
      const pairsB: number[] = []
      for (const row of rows) {
        const a = toNumber(row[colA])
        const b = toNumber(row[colB])
        if (a != null && b != null) {
          pairsA.push(a)
          pairsB.push(b)
        }
      }
      const coefficient = pearson(pairsA, pairsB)
      if (Math.abs(coefficient) >= 0.35 && pairsA.length >= 5) {
        correlations.push({ colA, colB, coefficient })
      }
    }
  }
  correlations.sort((a, b) => Math.abs(b.coefficient) - Math.abs(a.coefficient))

  const dup = duplicateStats(rows)
  const completeRowCount = rows.filter(row => result.columns!.every(col => !isNullish(row[col]))).length

  return {
    rowCount,
    columnCount: result.columns.length,
    numericColumnCount: numericColumns.length,
    stringColumnCount: columns.filter(c => c.kind === 'string').length,
    duplicateRowCount: dup.duplicateRowCount,
    duplicateRate: dup.duplicateRate,
    completeRowCount,
    completeRowRate: rowCount ? (completeRowCount / rowCount) * 100 : 0,
    columns,
    insights: buildInsights(columns, dup.duplicateRate, rowCount),
    correlations: correlations.slice(0, 10)
  }
}

export function analyzeSubset(
  rows: Record<string, unknown>[],
  columns: string[]
): ResultAnalytics | null {
  if (!rows.length || !columns.length) return null
  return analyzeResult({
    type: 'SELECT',
    columns,
    rows,
    rowCount: rows.length
  })
}

export function buildGroupAggregates(
  result: SqlExecuteResponse | null,
  groupColumn: string
): GroupAggregateRow[] {
  if (!result?.rows?.length || !result.columns?.includes(groupColumn)) return []

  const numericColumns = result.columns.filter(col => {
    if (col === groupColumn) return false
    const values = result.rows!.map(row => row[col]).filter(v => !isNullish(v))
    const kind = inferKind(values)
    return kind === 'number' || kind === 'integer'
  })

  const groups = new Map<string, Record<string, unknown>[]>()
  for (const row of result.rows) {
    const key = isNullish(row[groupColumn]) ? '(NULL)' : String(row[groupColumn])
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(row)
  }

  return [...groups.entries()]
    .map(([groupValue, rows]) => {
      const metrics: GroupAggregateRow['metrics'] = {}
      for (const col of numericColumns) {
        const nums = rows.map(r => toNumber(r[col])).filter((v): v is number => v != null)
        if (!nums.length) continue
        metrics[col] = {
          sum: nums.reduce((s, v) => s + v, 0),
          avg: nums.reduce((s, v) => s + v, 0) / nums.length,
          min: Math.min(...nums),
          max: Math.max(...nums)
        }
      }
      return { groupValue, count: rows.length, metrics }
    })
    .sort((a, b) => b.count - a.count)
}

export function kindLabel(kind: ColumnKind) {
  const map: Record<ColumnKind, string> = {
    number: '数值',
    integer: '整数',
    boolean: '布尔',
    date: '日期',
    datetime: '日期时间',
    string: '文本',
    empty: '空列',
    mixed: '混合'
  }
  return map[kind]
}

export function formatMetric(value?: number) {
  if (value == null || Number.isNaN(value)) return '-'
  return formatNumber(value)
}

export function correlationLabel(value: number) {
  const abs = Math.abs(value)
  if (abs >= 0.8) return '强相关'
  if (abs >= 0.5) return '中等相关'
  if (abs >= 0.35) return '弱相关'
  return '无明显相关'
}
