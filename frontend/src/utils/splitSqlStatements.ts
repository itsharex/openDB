function isOnlyCommentsOrEmpty(sql: string): boolean {
  return sql
    .split('\n')
    .every(line => {
      const trimmed = line.trim()
      return !trimmed || trimmed.startsWith('--') || trimmed.startsWith('/*') || trimmed.startsWith('*')
    })
}

/** Split SQL script into executable statements by semicolon (respects quotes). */
export function splitSqlStatements(sql: string): string[] {
  const statements: string[] = []
  let current = ''
  let inSingle = false
  let inDouble = false
  let inBacktick = false

  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    const prev = sql[i - 1]

    if (ch === "'" && !inDouble && !inBacktick && prev !== '\\') inSingle = !inSingle
    else if (ch === '"' && !inSingle && !inBacktick && prev !== '\\') inDouble = !inDouble
    else if (ch === '`' && !inSingle && !inDouble) inBacktick = !inBacktick

    if (ch === ';' && !inSingle && !inDouble && !inBacktick) {
      const stmt = current.trim()
      if (stmt && !isOnlyCommentsOrEmpty(stmt)) statements.push(stmt)
      current = ''
      continue
    }
    current += ch
  }

  const last = current.trim()
  if (last && !isOnlyCommentsOrEmpty(last)) statements.push(last)
  return statements
}

export function resolveSqlStatements(allSql: string, selectedSql?: string, mode: 'all' | 'selection' = 'all'): string[] {
  const selected = selectedSql?.trim()
  if (mode === 'selection') {
    if (!selected) return []
    return splitSqlStatements(selected)
  }
  if (selected) return splitSqlStatements(selected)
  return splitSqlStatements(allSql)
}
