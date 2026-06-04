import type { SqlExecuteResponse } from '@/types'

export interface ExplainStep {
  id: number
  table?: string
  type?: string
  possibleKeys?: string
  key?: string
  rows?: number
  extra?: string
  raw: Record<string, unknown>
}

export interface ExplainAnalytics {
  stepCount: number
  fullScans: ExplainStep[]
  indexScans: ExplainStep[]
  highCostSteps: ExplainStep[]
  hasFilesort: boolean
  hasTemporary: boolean
  estimatedRows: number
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

function pick(row: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    if (row[key] != null) return row[key]
    const lower = key.toLowerCase()
    const hit = Object.keys(row).find(k => k.toLowerCase() === lower)
    if (hit && row[hit] != null) return row[hit]
  }
  return undefined
}

export function isExplainResult(result: SqlExecuteResponse | null): boolean {
  if (!result || result.type !== 'SELECT' || !result.columns?.length || !result.rows?.length) return false
  const cols = result.columns.map(c => c.toLowerCase())
  return cols.includes('type') && (cols.includes('table') || cols.includes('relation')) && cols.includes('rows')
}

export function analyzeExplain(result: SqlExecuteResponse | null): ExplainAnalytics | null {
  if (!isExplainResult(result) || !result?.rows?.length || !result.columns) return null

  const steps: ExplainStep[] = result.rows.map((row, index) => {
    const type = String(pick(row, ['type', 'TYPE']) ?? '')
    const table = pick(row, ['table', 'TABLE', 'relation']) as string | undefined
    const possibleKeys = pick(row, ['possible_keys', 'POSSIBLE_KEYS']) as string | undefined
    const key = pick(row, ['key', 'KEY']) as string | undefined
    const rowsVal = Number(pick(row, ['rows', 'ROWS']) ?? 0)
    const extra = String(pick(row, ['Extra', 'extra', 'EXTRA']) ?? '')
    return { id: index + 1, table, type, possibleKeys, key, rows: rowsVal, extra, raw: row }
  })

  const fullScans = steps.filter(s => (s.type ?? '').toUpperCase() === 'ALL')
  const indexScans = steps.filter(s => {
    const t = (s.type ?? '').toUpperCase()
    return t === 'ref' || t === 'eq_ref' || t === 'range' || t === 'index'
  })
  const highCostSteps = steps.filter(s => (s.rows ?? 0) >= 1000)
  const hasFilesort = steps.some(s => (s.extra ?? '').toLowerCase().includes('filesort'))
  const hasTemporary = steps.some(s => (s.extra ?? '').toLowerCase().includes('temporary'))
  const estimatedRows = steps.reduce((s, step) => s + (step.rows ?? 0), 0)

  const insights: ExplainAnalytics['insights'] = []
  if (fullScans.length) {
    insights.push({
      level: 'warn',
      title: '全表扫描',
      detail: `${fullScans.length} 步 type=ALL（表: ${fullScans.map(s => s.table || '?').join(', ')}），建议检查 WHERE 条件与索引。`
    })
  } else {
    insights.push({ level: 'good', title: '无全表扫描', detail: '执行计划未出现 ALL 类型扫描。' })
  }
  if (hasFilesort) {
    insights.push({ level: 'warn', title: 'Using filesort', detail: '存在文件排序，可考虑为 ORDER BY 列添加合适索引。' })
  }
  if (hasTemporary) {
    insights.push({ level: 'warn', title: 'Using temporary', detail: '使用临时表，GROUP BY / DISTINCT 可能较昂贵。' })
  }
  if (highCostSteps.length) {
    insights.push({
      level: 'info',
      title: '高估算行数',
      detail: `${highCostSteps.length} 步 rows ≥ 1000，总估算 ${estimatedRows.toLocaleString()} 行。`
    })
  }
  for (const step of steps) {
    if (step.possibleKeys && !step.key) {
      insights.push({
        level: 'info',
        title: `未使用索引: ${step.table ?? '?'}`,
        detail: `possible_keys=${step.possibleKeys}，但实际 key 为空。`
      })
      break
    }
  }

  return {
    stepCount: steps.length,
    fullScans,
    indexScans,
    highCostSteps,
    hasFilesort,
    hasTemporary,
    estimatedRows,
    insights
  }
}
