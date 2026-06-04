export type InferredSqlType = 'INT' | 'BIGINT' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'VARCHAR' | 'TEXT'

export interface CsvColumnProfile {
  name: string
  inferredType: InferredSqlType
  suggestedLength: number
  nullCount: number
  nullRate: number
  distinctCount: number
  sampleValues: string[]
  maxLength: number
}

export interface CsvAnalytics {
  rowCount: number
  columnCount: number
  delimiter: string
  emptyRowCount: number
  duplicateRowCount: number
  columns: CsvColumnProfile[]
  insights: Array<{ level: 'good' | 'info' | 'warn'; title: string; detail: string }>
}

function detectDelimiter(text: string): string {
  const sample = text.split(/\r?\n/).slice(0, 5).join('\n')
  const commas = (sample.match(/,/g) ?? []).length
  const tabs = (sample.match(/\t/g) ?? []).length
  const semis = (sample.match(/;/g) ?? []).length
  if (tabs > commas && tabs > semis) return '\t'
  if (semis > commas) return ';'
  return ','
}

function parseLine(line: string, delimiter: string): string[] {
  const result: string[] = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"'
        i++
      } else {
        inQuotes = !inQuotes
      }
    } else if (ch === delimiter && !inQuotes) {
      result.push(current)
      current = ''
    } else {
      current += ch
    }
  }
  result.push(current)
  return result.map(v => v.trim())
}

function isEmptyValue(v: string) {
  return v === '' || v.toLowerCase() === 'null'
}

function inferType(values: string[]): { type: InferredSqlType; length: number } {
  const nonEmpty = values.filter(v => !isEmptyValue(v))
  if (!nonEmpty.length) return { type: 'VARCHAR', length: 255 }

  let allInt = true
  let allDecimal = true
  let allBool = true
  let allDate = true
  let allDateTime = true
  let maxLen = 0

  for (const raw of nonEmpty) {
    const v = raw.trim()
    maxLen = Math.max(maxLen, v.length)
    if (!/^-?\d+$/.test(v)) allInt = false
    if (!/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(v)) allDecimal = false
    if (!/^(true|false|0|1|yes|no)$/i.test(v)) allBool = false
    if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) allDate = false
    if (!/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(v)) allDateTime = false
  }

  if (allBool) return { type: 'BOOLEAN', length: 1 }
  if (allInt) {
    const nums = nonEmpty.map(v => Number(v))
    const max = Math.max(...nums.map(Math.abs))
    return { type: max > 2147483647 ? 'BIGINT' : 'INT', length: 0 }
  }
  if (allDecimal) return { type: 'DECIMAL', length: 18 }
  if (allDateTime) return { type: 'DATETIME', length: 0 }
  if (allDate) return { type: 'DATE', length: 0 }
  if (maxLen > 255) return { type: 'TEXT', length: maxLen }
  return { type: 'VARCHAR', length: Math.min(65535, Math.max(32, Math.ceil(maxLen / 16) * 16)) }
}

export function analyzeCsv(csvContent: string, hasHeader = true): CsvAnalytics | null {
  const trimmed = csvContent.trim()
  if (!trimmed) return null

  const delimiter = detectDelimiter(trimmed)
  const lines = trimmed.split(/\r?\n/).filter(line => line.trim().length > 0)
  if (!lines.length) return null

  const parsed = lines.map(line => parseLine(line, delimiter))
  const width = Math.max(...parsed.map(r => r.length))
  const normalized = parsed.map(row => {
    const copy = [...row]
    while (copy.length < width) copy.push('')
    return copy.slice(0, width)
  })

  let headers: string[]
  let dataRows: string[][]

  if (hasHeader) {
    headers = normalized[0].map((h, i) => h || `column_${i + 1}`)
    dataRows = normalized.slice(1)
  } else {
    headers = Array.from({ length: width }, (_, i) => `column_${i + 1}`)
    dataRows = normalized
  }

  const emptyRowCount = dataRows.filter(row => row.every(isEmptyValue)).length
  const rowKeys = new Set<string>()
  let duplicateRowCount = 0
  for (const row of dataRows) {
    const key = row.join('\u0001')
    if (rowKeys.has(key)) duplicateRowCount++
    else rowKeys.add(key)
  }

  const columns: CsvColumnProfile[] = headers.map((name, colIndex) => {
    const values = dataRows.map(row => row[colIndex] ?? '')
    const nullCount = values.filter(isEmptyValue).length
    const distinct = new Set(values.filter(v => !isEmptyValue(v)))
    const inferred = inferType(values)
    return {
      name,
      inferredType: inferred.type,
      suggestedLength: inferred.length,
      nullCount,
      nullRate: values.length ? (nullCount / values.length) * 100 : 0,
      distinctCount: distinct.size,
      sampleValues: [...distinct].slice(0, 3),
      maxLength: Math.max(...values.map(v => v.length), 0)
    }
  })

  const insights: CsvAnalytics['insights'] = []
  if (duplicateRowCount > 0) {
    insights.push({
      level: 'warn',
      title: '重复行',
      detail: `检测到 ${duplicateRowCount} 行完全重复，导入前可考虑去重。`
    })
  }
  if (emptyRowCount > 0) {
    insights.push({ level: 'info', title: '空行', detail: `${emptyRowCount} 行为空，导入时将被跳过或写入 NULL。` })
  }
  for (const col of columns) {
    if (col.nullRate >= 40) {
      insights.push({
        level: 'warn',
        title: `${col.name} 空值较多`,
        detail: `空值占比 ${col.nullRate.toFixed(1)}%，请确认列映射是否正确。`
      })
    }
  }
  if (delimiter !== ',') {
    insights.push({
      level: 'info',
      title: '分隔符检测',
      detail: `自动识别分隔符为「${delimiter === '\t' ? 'Tab' : delimiter}」。`
    })
  }
  if (!insights.length) {
    insights.push({ level: 'good', title: '数据质量良好', detail: '未发现明显异常，可以导入。' })
  }

  return {
    rowCount: dataRows.length,
    columnCount: headers.length,
    delimiter,
    emptyRowCount,
    duplicateRowCount,
    columns,
    insights
  }
}

export function typeLabel(type: InferredSqlType) {
  const map: Record<InferredSqlType, string> = {
    INT: '整数',
    BIGINT: '长整数',
    DECIMAL: '小数',
    BOOLEAN: '布尔',
    DATE: '日期',
    DATETIME: '日期时间',
    VARCHAR: '文本',
    TEXT: '长文本'
  }
  return map[type]
}

export function formatSuggestedSqlType(col: CsvColumnProfile) {
  if (col.inferredType === 'VARCHAR' && col.suggestedLength) {
    return `VARCHAR(${col.suggestedLength})`
  }
  if (col.inferredType === 'DECIMAL') return 'DECIMAL(18,4)'
  return col.inferredType
}
