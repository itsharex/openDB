const KEYWORDS = new Set([
  'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AND', 'OR', 'NOT',
  'GROUP', 'BY', 'ORDER', 'HAVING', 'LIMIT', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'CREATE', 'ALTER', 'DROP', 'TABLE', 'DATABASE', 'INDEX', 'VIEW', 'AS', 'DISTINCT', 'UNION', 'ALL',
  'EXPLAIN', 'SHOW', 'DESC', 'DESCRIBE', 'NULL', 'IS', 'IN', 'LIKE', 'BETWEEN', 'COUNT', 'SUM', 'AVG'
])

const MULTI_WORD_KEYWORDS = [
  'UNION ALL',
  'INSERT INTO',
  'DELETE FROM',
  'CREATE TABLE',
  'ALTER TABLE',
  'DROP TABLE',
  'LEFT OUTER JOIN',
  'RIGHT OUTER JOIN',
  'INNER JOIN',
  'LEFT JOIN',
  'RIGHT JOIN',
  'OUTER JOIN',
  'ORDER BY',
  'GROUP BY'
]

const CLAUSE_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'JOIN', 'HAVING', 'LIMIT', 'OFFSET', 'SET', 'VALUES', 'INTO', 'EXPLAIN'
]

/** Uppercase keywords often glued to identifiers in AI output. */
const GLUED_INLINE_KEYWORDS = ['AS']

const CLAUSE_BOUNDARY = String.raw`(?=\s|$|[;,()]|\n|JOIN|WHERE|GROUP|ORDER|HAVING|LIMIT|SET|VALUES|UNION|;)`

function protectLiterals(sql: string) {
  const literals: string[] = []
  const protectedSql = sql.replace(/('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|`(?:[^`\\]|\\.)*`)/g, match => {
    literals.push(match)
    return `\x00L${literals.length - 1}\x00`
  })
  return { protectedSql, literals }
}

function restoreLiterals(sql: string, literals: string[]) {
  return sql.replace(/\x00L(\d+)\x00/g, (_, index) => literals[Number(index)])
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** Repair SQL where keywords and operators are glued together (common in AI output). */
export function repairSqlSpacing(sql: string) {
  let formatted = sql.trim()
  if (!formatted) return formatted

  const { protectedSql, literals } = protectLiterals(formatted)
  formatted = protectedSql

  formatted = formatted.replace(/\bLIMIT(\d+)/gi, 'LIMIT $1')
  formatted = formatted.replace(/\bOFFSET(\d+)/gi, 'OFFSET $1')

  for (const keyword of MULTI_WORD_KEYWORDS) {
    const glued = keyword.replace(/\s+/g, '')
    formatted = formatted.replace(new RegExp(glued, 'gi'), keyword)
  }

  for (const keyword of MULTI_WORD_KEYWORDS) {
    formatted = formatted.replace(
      new RegExp(`(${escapeRegExp(keyword)})(?=[A-Za-z0-9_.\`])`, 'gi'),
      '$1 '
    )
  }

  formatted = formatted.replace(
    /([A-Za-z0-9_]{2,})([A-Za-z])ON(?=[A-Za-z0-9_.])/g,
    '$1 $2 ON '
  )

  for (const keyword of GLUED_INLINE_KEYWORDS) {
    formatted = formatted.replace(
      new RegExp(`([A-Za-z0-9_.])${keyword}(?=[A-Za-z0-9_])`, 'g'),
      `$1 ${keyword} `
    )
  }

  formatted = formatted.replace(/([A-Za-z0-9_.\)])DESC(?=\s|;|$|\n)/gi, '$1 DESC')
  formatted = formatted.replace(/([A-Za-z0-9_.\)])ASC(?=\s|;|$|\n)/gi, '$1 ASC')

  for (const keyword of CLAUSE_KEYWORDS) {
    formatted = formatted.replace(
      new RegExp(`(^|[\\s,(])${keyword}(?=[A-Za-z0-9_])`, 'gi'),
      `$1${keyword} `
    )
  }

  formatted = formatted.replace(
    new RegExp(`\\bFROM\\s+([A-Za-z0-9_]{2,})([A-Za-z])${CLAUSE_BOUNDARY}`, 'i'),
    'FROM $1 $2'
  )

  formatted = formatted.replace(/\s*([=<>!]+)\s*/g, ' $1 ')
  formatted = formatted.replace(/\s*,\s*/g, ', ')
  formatted = formatted.replace(/\s*;\s*/g, '; ')
  formatted = formatted.replace(/[ \t]+/g, ' ')
  formatted = formatted.replace(/\s*\n\s*/g, '\n')

  return restoreLiterals(formatted, literals).trim()
}

export function formatSqlBasic(sql: string) {
  let formatted = repairSqlSpacing(sql)
  formatted = formatted.replace(/\s+/g, ' ')
  formatted = formatted.replace(/\s*,\s*/g, ', ')
  formatted = formatted.replace(/\s*;\s*/g, ';\n')
  formatted = formatted.replace(
    /\b(SELECT|FROM|WHERE|JOIN|LEFT JOIN|RIGHT JOIN|INNER JOIN|GROUP BY|ORDER BY|HAVING|LIMIT|INSERT INTO|UPDATE|DELETE FROM|SET|VALUES|CREATE TABLE|ALTER TABLE|DROP TABLE)\b/gi,
    '\n$1'
  )
  formatted = formatted
    .split('\n')
    .map(line => {
      const trimmed = line.trim()
      if (!trimmed) return ''
      const upper = trimmed.split(/\s+/)[0]?.toUpperCase()
      if (upper && KEYWORDS.has(upper)) {
        return trimmed.replace(/^(\w+)/, match => match.toUpperCase())
      }
      return trimmed
    })
    .filter(Boolean)
    .join('\n')
  return formatted.trim()
}

/** Extract SQL from AI markdown/plain text. */
export function extractAiSql(content: string): string | null {
  const fenced = content.match(/```sql\s*([\s\S]*?)```/i)
  if (fenced?.[1]?.trim()) {
    return formatAiSql(fenced[1].trim())
  }

  const selectMatch = content.match(/(SELECT[\s\S]*?)(?:;|\n\n|$)/i)
  if (selectMatch?.[1]?.trim()) {
    return formatAiSql(selectMatch[1].trim().replace(/;+\s*$/, ''))
  }

  return null
}

/** Normalize AI-generated SQL before inserting into the editor. */
export function formatAiSql(sql: string) {
  return formatSqlBasic(sql)
}
