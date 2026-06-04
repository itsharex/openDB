import type { DataCompareResult } from '@/types/features'

export interface TransferPreview {
  sourceRows: number
  targetRows: number
  rowDiff: number
  willTruncate: boolean
  estimatedImpact: string
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

export interface TransferResultStats {
  transferred: number
  durationMs: number
  rowsPerSecond: number
  truncateTarget: boolean
  sourceLabel: string
  targetLabel: string
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

export function buildTransferPreview(
  compare: DataCompareResult,
  truncateTarget: boolean
): TransferPreview {
  const rowDiff = compare.targetRows - compare.sourceRows
  const insights: TransferPreview['insights'] = []

  if (truncateTarget) {
    insights.push({
      level: 'warn',
      title: '将清空目标表',
      detail: `目标表当前 ${compare.targetRows.toLocaleString()} 行将被 TRUNCATE 后写入。`
    })
  } else if (compare.targetRows > 0) {
    insights.push({
      level: 'info',
      title: '追加模式',
      detail: `目标表已有 ${compare.targetRows.toLocaleString()} 行，新数据将追加写入（可能产生重复）。`
    })
  }

  if (compare.sourceRows === 0) {
    insights.push({ level: 'warn', title: '源表为空', detail: '源表没有可传输的数据。' })
  } else {
    insights.push({
      level: 'good',
      title: '待传输数据量',
      detail: `预计传输 ${compare.sourceRows.toLocaleString()} 行。`
    })
  }

  if (!truncateTarget && compare.targetRows > 0 && compare.sourceRows > 0) {
    const afterEstimate = compare.targetRows + compare.sourceRows
    insights.push({
      level: 'info',
      title: '传输后预估',
      detail: `目标表行数约变为 ${afterEstimate.toLocaleString()} 行（追加估算）。`
    })
  }

  return {
    sourceRows: compare.sourceRows,
    targetRows: compare.targetRows,
    rowDiff,
    willTruncate: truncateTarget,
    estimatedImpact: truncateTarget
      ? `清空并写入 ${compare.sourceRows.toLocaleString()} 行`
      : `追加 ${compare.sourceRows.toLocaleString()} 行`,
    insights
  }
}

export function buildTransferResultStats(
  transferred: number,
  durationMs: number,
  truncateTarget: boolean,
  sourceLabel: string,
  targetLabel: string
): TransferResultStats {
  const rowsPerSecond = durationMs > 0 ? (transferred / durationMs) * 1000 : 0
  const insights: TransferResultStats['insights'] = []

  if (transferred === 0) {
    insights.push({ level: 'warn', title: '未传输数据', detail: '传输完成但没有写入任何行。' })
  } else {
    insights.push({
      level: 'good',
      title: '传输完成',
      detail: `成功传输 ${transferred.toLocaleString()} 行，耗时 ${(durationMs / 1000).toFixed(2)}s。`
    })
  }

  if (rowsPerSecond > 0) {
    const speedLabel = rowsPerSecond >= 1000
      ? `${(rowsPerSecond / 1000).toFixed(1)}k 行/秒`
      : `${rowsPerSecond.toFixed(0)} 行/秒`
    insights.push({
      level: rowsPerSecond < 100 ? 'info' : 'good',
      title: '吞吐速率',
      detail: `平均 ${speedLabel}${rowsPerSecond < 100 ? '，大批量传输可考虑分批或关闭索引后导入。' : '。'}`
    })
  }

  if (truncateTarget) {
    insights.push({ level: 'info', title: '目标表已清空', detail: `${targetLabel} 在传输前已执行 TRUNCATE。` })
  }

  return {
    transferred,
    durationMs,
    rowsPerSecond,
    truncateTarget,
    sourceLabel,
    targetLabel,
    insights
  }
}

export type TransferPhase = 'idle' | 'preview' | 'transferring' | 'done' | 'error'

export function phaseLabel(phase: TransferPhase) {
  const map: Record<TransferPhase, string> = {
    idle: '就绪',
    preview: '预检中...',
    transferring: '传输中...',
    done: '完成',
    error: '失败'
  }
  return map[phase]
}
