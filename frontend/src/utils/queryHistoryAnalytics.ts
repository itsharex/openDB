import type { QueryHistoryItem } from '@/types/features'

export interface QueryHistoryAnalytics {
  total: number
  successCount: number
  failCount: number
  successRate: number
  avgDurationMs: number
  maxDurationMs: number
  totalRowsReturned: number
  slowQueries: QueryHistoryItem[]
  recentByDay: Array<{ label: string; count: number }>
  topKeywords: Array<{ word: string; count: number }>
  byDatabase: Array<{ name: string; count: number }>
  byConnection: Array<{ name: string; count: number }>
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

const STOP_WORDS = new Set([
  'select', 'from', 'where', 'and', 'or', 'join', 'left', 'right', 'inner', 'on', 'as',
  'group', 'order', 'by', 'limit', 'insert', 'update', 'delete', 'into', 'values', 'set',
  'null', 'not', 'in', 'like', 'between', 'distinct', 'count', 'sum', 'avg', 'max', 'min'
])

function extractKeywords(sql: string): string[] {
  return sql
    .toLowerCase()
    .replace(/[`"'[\]]/g, ' ')
    .split(/\W+/)
    .filter(w => w.length >= 3 && !STOP_WORDS.has(w))
}

export function analyzeQueryHistory(items: QueryHistoryItem[]): QueryHistoryAnalytics {
  const total = items.length
  const successCount = items.filter(i => i.success).length
  const failCount = total - successCount
  const durations = items.map(i => i.durationMs).filter((v): v is number => v != null && v >= 0)
  const avgDurationMs = durations.length ? durations.reduce((s, v) => s + v, 0) / durations.length : 0
  const maxDurationMs = durations.length ? Math.max(...durations) : 0
  const totalRowsReturned = items.reduce((s, i) => s + (i.rowCount ?? 0), 0)
  const slowQueries = items.filter(i => (i.durationMs ?? 0) >= 1000).slice(0, 8)

  const dayMap = new Map<string, number>()
  for (const item of items) {
    const label = new Date(item.executedAt).toLocaleDateString()
    dayMap.set(label, (dayMap.get(label) ?? 0) + 1)
  }
  const recentByDay = [...dayMap.entries()]
    .map(([label, count]) => ({ label, count }))
    .slice(0, 7)

  const keywordMap = new Map<string, number>()
  for (const item of items) {
    for (const word of extractKeywords(item.sql)) {
      keywordMap.set(word, (keywordMap.get(word) ?? 0) + 1)
    }
  }
  const topKeywords = [...keywordMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([word, count]) => ({ word, count }))

  const dbMap = new Map<string, number>()
  for (const item of items) {
    const name = item.database || '(未指定)'
    dbMap.set(name, (dbMap.get(name) ?? 0) + 1)
  }
  const byDatabase = [...dbMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count }))

  const connMap = new Map<string, number>()
  for (const item of items) {
    const name = item.connectionName || '(未知连接)'
    connMap.set(name, (connMap.get(name) ?? 0) + 1)
  }
  const byConnection = [...connMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count }))

  const insights: QueryHistoryAnalytics['insights'] = []
  if (total === 0) {
    insights.push({ level: 'info', title: '暂无历史', detail: '执行 SQL 后会自动记录查询历史。' })
  } else {
    if (failCount > 0) {
      insights.push({
        level: failCount / total > 0.2 ? 'warn' : 'info',
        title: '失败查询',
        detail: `${failCount} 次失败（${((failCount / total) * 100).toFixed(1)}%），建议检查语法或权限。`
      })
    }
    if (slowQueries.length) {
      insights.push({
        level: 'warn',
        title: '慢查询',
        detail: `${slowQueries.length} 条查询耗时 ≥ 1s，可用 EXPLAIN 或智能分析优化。`
      })
    }
    if (successRate(total, successCount) >= 95) {
      insights.push({ level: 'good', title: '执行稳定', detail: `成功率 ${successRate(total, successCount).toFixed(1)}%。` })
    }
  }

  return {
    total,
    successCount,
    failCount,
    successRate: successRate(total, successCount),
    avgDurationMs,
    maxDurationMs,
    totalRowsReturned,
    slowQueries,
    recentByDay,
    topKeywords,
    byDatabase,
    byConnection,
    insights
  }
}

function successRate(total: number, success: number) {
  return total ? (success / total) * 100 : 0
}

export function classifySql(sql: string): string {
  const head = sql.trim().split(/\s+/)[0]?.toUpperCase() ?? 'OTHER'
  if (['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'DROP', 'EXPLAIN', 'SHOW'].includes(head)) {
    return head
  }
  return 'OTHER'
}
