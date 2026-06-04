export interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export interface DatabaseTypeInfo {
  value: string
  label: string
  defaultPort: number
  supported: boolean
}

export interface ConnectionRequest {
  type: string
  name: string
  host: string
  port: number
  username: string
  password?: string
  database?: string
}

export interface ConnectionInfo {
  id: string
  profileId?: string
  name: string
  type: string
  host: string
  port: number
  username: string
  database?: string
  connected: boolean
}

export interface ConnectionProfile {
  id: string
  name: string
  type: string
  host: string
  port: number
  username: string
  database?: string
  updatedAt: number
}

export interface DatabaseObjectInfo {
  name: string
  type?: string
  comment?: string
}

export interface DdlResponse {
  ddl: string
  objectType?: string
  objectName?: string
}

export interface IndexInfo {
  name: string
  columnName: string
  unique: boolean
  type?: string
}

export interface TableInfo {
  name: string
  engine?: string
  rows?: number
  comment?: string
}

export interface ColumnInfo {
  name: string
  type: string
  nullable?: string
  key?: string
  defaultValue?: string
  extra?: string
}

export interface SqlExecuteRequest {
  sql: string
  database?: string
  limit?: number
}

export interface SqlExecuteResponse {
  type: string
  columns?: string[]
  rows?: Record<string, unknown>[]
  rowCount?: number
  executionTimeMs?: number
  message?: string
  editable?: boolean
  sourceDatabase?: string
  sourceTable?: string
  primaryKeys?: string[]
  editableReason?: string
}

export interface AiChatRequest {
  prompt: string
  database?: string
  contextSql?: string
}

export interface AiChatResponse {
  content: string
  enabled: boolean
}

export interface AiStatus {
  enabled: boolean
  configured: boolean
  provider: string
  providerLabel: string
  model: string
  apiUrl: string
}

export interface AiConfig {
  enabled: boolean
  provider: string
  providerLabel: string
  apiUrl: string
  model: string
  apiVersion: string
  timeoutSeconds: number
  temperature: number
  maxTokens: number
  hasApiKey: boolean
  apiKeyMasked: string
}

export interface AiConfigRequest {
  enabled: boolean
  provider: string
  apiUrl: string
  apiKey?: string
  model: string
  apiVersion?: string
  timeoutSeconds: number
  temperature: number
  maxTokens: number
}

export interface AiProviderPreset {
  id: string
  label: string
  defaultApiUrl: string
  defaultModel: string
  suggestedModels: string[]
  apiKeyRequired: boolean
  description?: string
}

export interface SchemaSummary {
  database: string
  tables: TableSchema[]
}

export interface TableSchema {
  name: string
  columns: ColumnInfo[]
}

export interface SqlSchemaContext {
  databases: string[]
  schema: SchemaSummary | null
}

export interface TreeNode {
  id: string
  label: string
  type:
    | 'connection'
    | 'database'
    | 'folder'
    | 'table'
    | 'view'
    | 'procedure'
    | 'function'
    | 'trigger'
    | 'columns'
    | 'indexes'
  connectionId?: string
  database?: string
  table?: string
  objectName?: string
  folderKind?: 'tables' | 'views' | 'procedures' | 'functions' | 'triggers'
  children?: TreeNode[]
  expanded?: boolean
  loading?: boolean
}

export * from './features'
