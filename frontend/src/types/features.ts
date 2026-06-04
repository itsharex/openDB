export type BackgroundFit = 'cover' | 'contain' | 'tile'

export interface AppSettings {
  editorFontSize: number
  queryLimit: number
  confirmDestructive: boolean
  autoSaveProfile: boolean
  lastProfileId?: string
  lastDatabase?: string
  backgroundEnabled: boolean
  backgroundFit: BackgroundFit
  backgroundPanelOpacity: number
  backgroundBlur: number
}

export interface QueryHistoryItem {
  id: string
  sql: string
  database?: string
  connectionName?: string
  executedAt: number
  success: boolean
  rowCount?: number
  durationMs?: number
}

export interface ContextMenuItem {
  id: string
  label: string
  action: string
  divider?: boolean
  danger?: boolean
  disabled?: boolean
}

export interface ObjectSelection {
  connectionId: string
  database: string
  objectType: 'table' | 'view' | 'procedure' | 'function' | 'trigger'
  objectName: string
}

export interface TableEditContext {
  connectionId: string
  database: string
  table: string
  primaryKeys: string[]
}

export interface ErDiagramData {
  database: string
  tables: Array<{ name: string; columns: Array<{ name: string; type: string; key?: string }> }>
  relationships: Array<{
    fromTable: string
    fromColumn: string
    toTable: string
    toColumn: string
    constraintName?: string
  }>
}

export interface SchemaCompareResult {
  sourceDatabase: string
  targetDatabase: string
  onlyInSource: string[]
  onlyInTarget: string[]
  modifiedTables: string[]
  syncScript: string
}

export interface DataCompareResult {
  sourceDatabase: string
  sourceTable: string
  targetDatabase: string
  targetTable: string
  sourceRows: number
  targetRows: number
  summary: string
}

export interface DesignerColumn {
  name: string
  type: string
  length: string
  nullable: boolean
  primaryKey: boolean
  autoIncrement: boolean
  defaultValue: string
}
