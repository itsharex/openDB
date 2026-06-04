import type { SqlExecuteResponse } from '@/types'

export function exportResultToCsv(result: SqlExecuteResponse, filename = 'query-result.csv') {
  if (!result.columns?.length || !result.rows?.length) {
    throw new Error('没有可导出的数据')
  }
  const lines = [result.columns.map(csvEscape).join(',')]
  for (const row of result.rows) {
    lines.push(result.columns.map(col => csvEscape(formatCell(row[col]))).join(','))
  }
  downloadText(lines.join('\n'), filename, 'text/csv;charset=utf-8')
}

const UTF8_BOM = '\uFEFF'

export function downloadText(content: string, filename: string, mime: string) {
  const payload = mime.includes('csv') ? UTF8_BOM + content : content
  const blob = new Blob([payload], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function formatCell(value: unknown) {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function csvEscape(value: string) {
  if (value.includes(',') || value.includes('"') || value.includes('\n')) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}
