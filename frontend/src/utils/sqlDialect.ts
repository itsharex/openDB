import { MySQL, PostgreSQL, StandardSQL } from '@codemirror/lang-sql'

const TYPE_LABELS: Record<string, string> = {
  MYSQL: 'MySQL',
  POSTGRESQL: 'PostgreSQL',
  ORACLE: 'Oracle',
  H2: 'H2'
}

export function getDatabaseTypeLabel(type?: string) {
  if (!type) return 'MySQL'
  return TYPE_LABELS[type.toUpperCase()] ?? type
}

export function getCodeMirrorDialect(type?: string) {
  switch (type?.toUpperCase()) {
    case 'POSTGRESQL':
      return PostgreSQL
    case 'ORACLE':
    case 'H2':
      return StandardSQL
    default:
      return MySQL
  }
}

export function getDefaultPort(type?: string) {
  switch (type?.toUpperCase()) {
    case 'POSTGRESQL':
      return 5432
    case 'ORACLE':
      return 1521
    case 'H2':
      return 9092
    default:
      return 3306
  }
}

export function getDatabaseFieldHint(type?: string) {
  switch (type?.toUpperCase()) {
    case 'ORACLE':
      return 'Service Name / SID，例如 ORCL'
    case 'H2':
      return '数据库名，本地默认 mem:opendb'
    case 'POSTGRESQL':
      return '默认数据库，例如 postgres'
    default:
      return '可选，连接后可切换'
  }
}

export function quoteIdentifier(type: string | undefined, name: string) {
  switch (type?.toUpperCase()) {
    case 'POSTGRESQL':
    case 'H2':
      return `"${name.replace(/"/g, '""')}"`
    case 'ORACLE':
      return `"${name.replace(/"/g, '""').toUpperCase()}"`
    default:
      return `\`${name.replace(/`/g, '``')}\``
  }
}
