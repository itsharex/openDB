import type { Completion, CompletionContext, CompletionResult } from '@codemirror/autocomplete'
import type { SchemaSummary } from '@/types'

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN',
  'ON', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'INSERT INTO', 'UPDATE',
  'DELETE', 'CREATE TABLE', 'ALTER TABLE', 'DROP TABLE', 'SHOW TABLES',
  'SHOW DATABASES', 'DESC', 'DESCRIBE', 'EXPLAIN', 'AS', 'AND', 'OR', 'NOT',
  'IN', 'LIKE', 'BETWEEN', 'IS NULL', 'IS NOT NULL', 'COUNT', 'SUM', 'AVG',
  'MAX', 'MIN', 'DISTINCT', 'UNION', 'VALUES', 'SET'
]

export function buildCodeMirrorSchema(
  schema: SchemaSummary | null,
  databases: string[]
): Record<string, readonly string[]> | undefined {
  if (!schema && databases.length === 0) {
    return undefined
  }

  const result: Record<string, string[]> = {}

  for (const database of databases) {
    result[database] = database === schema?.database
      ? schema.tables.map(table => table.name)
      : []
  }

  if (schema) {
    for (const table of schema.tables) {
      result[table.name] = table.columns.map(column => column.name)
    }
  }

  return result
}

export function createSchemaCompletion(getSchema: () => SchemaSummary | null, getDatabases: () => string[]) {
  return (context: CompletionContext): CompletionResult | null => {
    const word = context.matchBefore(/[`'"]?[\w.]+[`'"]?/)
    if (!word && !context.explicit) {
      return null
    }

    const from = word ? word.from : context.pos
    const text = word?.text ?? ''
    const options: Completion[] = []
    const schema = getSchema()
    const databases = getDatabases()

    if (text.includes('.')) {
      const [tableName] = text.split('.')
      const table = schema?.tables.find(item => item.name.toLowerCase() === tableName.toLowerCase())
      if (table) {
        for (const column of table.columns) {
          options.push({
            label: column.name,
            type: 'property',
            detail: column.type,
            apply: column.name
          })
        }
      }
    } else {
      for (const keyword of SQL_KEYWORDS) {
        if (keyword.toLowerCase().startsWith(text.toLowerCase())) {
          options.push({
            label: keyword,
            type: 'keyword',
            boost: -1
          })
        }
      }

      for (const database of databases) {
        if (database.toLowerCase().startsWith(text.toLowerCase())) {
          options.push({
            label: database,
            type: 'namespace',
            detail: 'database'
          })
        }
      }

      if (schema) {
        for (const table of schema.tables) {
          if (table.name.toLowerCase().startsWith(text.toLowerCase())) {
            options.push({
              label: table.name,
              type: 'class',
              detail: `${table.columns.length} columns`
            })
          }
        }

        for (const table of schema.tables) {
          for (const column of table.columns) {
            if (column.name.toLowerCase().startsWith(text.toLowerCase())) {
              options.push({
                label: column.name,
                type: 'property',
                detail: `${table.name}.${column.type}`
              })
            }
          }
        }
      }
    }

    if (options.length === 0) {
      return null
    }

    return { from, options, validFor: /^[`'"]?[\w.]*[`'"]?$/ }
  }
}
