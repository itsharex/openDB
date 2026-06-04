import axios, { AxiosError } from 'axios'
import { readAiSseStream } from '@/utils/aiStream'
import type {
  AiChatRequest,
  AiChatResponse,
  AiConfig,
  AiConfigRequest,
  AiProviderPreset,
  AiStatus,
  ApiResponse,
  ColumnInfo,
  ConnectionInfo,
  ConnectionProfile,
  ConnectionRequest,
  DatabaseObjectInfo,
  DatabaseTypeInfo,
  DdlResponse,
  IndexInfo,
  SchemaSummary,
  SqlExecuteRequest,
  SqlExecuteResponse,
  TableInfo
} from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

http.interceptors.response.use(
  response => response,
  error => {
    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const message =
      axiosError.response?.data?.message ||
      (axiosError.code === 'ERR_NETWORK'
        ? '无法连接后端服务，请确认 backend 已启动 (http://localhost:8080)'
        : axiosError.message || '请求失败')
    return Promise.reject(new Error(message))
  }
)

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await promise
  if (!data.success) throw new Error(data.message || 'Request failed')
  return data.data
}

export const api = {
  getDatabaseTypes: () => unwrap<DatabaseTypeInfo[]>(http.get('/database-types')),

  listProfiles: () => unwrap<ConnectionProfile[]>(http.get('/profiles')),
  saveProfile: (payload: ConnectionRequest) => unwrap<ConnectionProfile>(http.post('/profiles', payload)),
  upsertProfile: (payload: ConnectionRequest) => unwrap<ConnectionProfile>(http.post('/profiles/upsert', payload)),
  updateProfile: (id: string, payload: ConnectionRequest) =>
    unwrap<ConnectionProfile>(http.put(`/profiles/${id}`, payload)),
  deleteProfile: (id: string) => unwrap<void>(http.delete(`/profiles/${id}`)),
  connectProfile: (id: string) => unwrap<ConnectionInfo>(http.post(`/profiles/${id}/connect`)),

  listConnections: () => unwrap<ConnectionInfo[]>(http.get('/connections')),
  testConnection: (payload: ConnectionRequest) =>
    unwrap<ConnectionInfo>(http.post('/connections/test', payload)),
  createConnection: (payload: ConnectionRequest) =>
    unwrap<ConnectionInfo>(http.post('/connections', payload)),
  disconnect: (id: string) => unwrap<void>(http.delete(`/connections/${id}`)),

  listDatabases: (connectionId: string) =>
    unwrap<string[]>(http.get(`/connections/${connectionId}/databases`)),
  createDatabase: (connectionId: string, payload: { name: string; charset?: string; collation?: string }) =>
    unwrap<void>(http.post(`/connections/${connectionId}/databases`, payload)),
  dropDatabase: (connectionId: string, database: string) =>
    unwrap<void>(http.delete(`/connections/${connectionId}/databases/${database}`)),

  listTables: (connectionId: string, database: string) =>
    unwrap<TableInfo[]>(http.get(`/connections/${connectionId}/databases/${database}/tables`)),
  listViews: (connectionId: string, database: string) =>
    unwrap<DatabaseObjectInfo[]>(http.get(`/connections/${connectionId}/databases/${database}/views`)),
  listProcedures: (connectionId: string, database: string) =>
    unwrap<DatabaseObjectInfo[]>(http.get(`/connections/${connectionId}/databases/${database}/procedures`)),
  listFunctions: (connectionId: string, database: string) =>
    unwrap<DatabaseObjectInfo[]>(http.get(`/connections/${connectionId}/databases/${database}/functions`)),
  listTriggers: (connectionId: string, database: string) =>
    unwrap<DatabaseObjectInfo[]>(http.get(`/connections/${connectionId}/databases/${database}/triggers`)),

  listColumns: (connectionId: string, database: string, table: string) =>
    unwrap<ColumnInfo[]>(
      http.get(`/connections/${connectionId}/databases/${database}/tables/${table}/columns`)
    ),
  listIndexes: (connectionId: string, database: string, table: string) =>
    unwrap<IndexInfo[]>(
      http.get(`/connections/${connectionId}/databases/${database}/tables/${table}/indexes`)
    ),
  showCreateTable: (connectionId: string, database: string, table: string) =>
    unwrap<DdlResponse>(http.get(`/connections/${connectionId}/databases/${database}/tables/${table}/ddl`)),
  showCreateView: (connectionId: string, database: string, view: string) =>
    unwrap<DdlResponse>(http.get(`/connections/${connectionId}/databases/${database}/views/${view}/ddl`)),

  createTable: (connectionId: string, database: string, ddl: string) =>
    unwrap<void>(http.post(`/connections/${connectionId}/databases/${database}/tables`, { ddl })),
  dropTable: (connectionId: string, database: string, table: string) =>
    unwrap<void>(http.delete(`/connections/${connectionId}/databases/${database}/tables/${table}`)),
  truncateTable: (connectionId: string, database: string, table: string) =>
    unwrap<void>(http.post(`/connections/${connectionId}/databases/${database}/tables/${table}/truncate`)),

  getSchema: (connectionId: string, database: string) =>
    unwrap<SchemaSummary>(http.get(`/connections/${connectionId}/databases/${database}/schema`)),
  previewTable: (connectionId: string, database: string, table: string, limit = 100) =>
    unwrap<SqlExecuteResponse>(
      http.get(`/connections/${connectionId}/databases/${database}/tables/${table}/preview`, { params: { limit } })
    ),
  executeSql: (connectionId: string, payload: SqlExecuteRequest) =>
    unwrap<SqlExecuteResponse>(http.post(`/connections/${connectionId}/query`, payload)),

  backupScript: (connectionId: string, database: string) =>
    unwrap<{ script: string }>(http.get(`/connections/${connectionId}/databases/${database}/backup-script`)),

  exportTableCsv: async (connectionId: string, database: string, table: string, limit = 10000) => {
    const response = await http.get(
      `/connections/${connectionId}/databases/${database}/tables/${table}/export`,
      { params: { limit }, responseType: 'blob' }
    )
    return response.data as Blob
  },

  getAiStatus: () => unwrap<AiStatus>(http.get('/ai/status')),
  getAiConfig: () => unwrap<AiConfig>(http.get('/ai/config')),
  updateAiConfig: (payload: AiConfigRequest) => unwrap<AiConfig>(http.put('/ai/config', payload)),
  listAiProviders: () => unwrap<AiProviderPreset[]>(http.get('/ai/providers')),
  testAiConfig: () => unwrap<{ reply: string }>(http.post('/ai/test')),
  aiChat: (connectionId: string, payload: AiChatRequest) =>
    unwrap<AiChatResponse>(http.post(`/connections/${connectionId}/ai/chat`, payload)),
  aiChatStream: async (
    connectionId: string,
    payload: AiChatRequest,
    handlers: {
      onDelta: (chunk: string) => void
      onStatus?: (status: string) => void
    },
    signal?: AbortSignal
  ) => {
    await readAiSseStream(
      await fetch(`/api/connections/${connectionId}/ai/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal
      }),
      handlers
    )
  },

  getTableMeta: (connectionId: string, database: string, table: string) =>
    unwrap<{ table: string; database: string; primaryKeys: string[]; columns: ColumnInfo[] }>(
      http.get(`/connections/${connectionId}/databases/${database}/tables/${table}/meta`)
    ),
  insertRow: (connectionId: string, database: string, table: string, payload: { data: Record<string, unknown> }) =>
    unwrap<void>(http.post(`/connections/${connectionId}/databases/${database}/tables/${table}/rows`, {
      database, table, data: payload.data
    })),
  updateRow: (connectionId: string, database: string, table: string, payload: { data: Record<string, unknown>; primaryKey: Record<string, unknown> }) =>
    unwrap<void>(http.put(`/connections/${connectionId}/databases/${database}/tables/${table}/rows`, {
      database, table, data: payload.data, primaryKey: payload.primaryKey
    })),
  deleteRow: (connectionId: string, database: string, table: string, payload: { data: Record<string, unknown>; primaryKey: Record<string, unknown> }) =>
    unwrap<void>(http.delete(`/connections/${connectionId}/databases/${database}/tables/${table}/rows`, { data: {
      database, table, data: payload.data, primaryKey: payload.primaryKey
    } })),
  importCsv: (connectionId: string, database: string, table: string, csvContent: string, hasHeader = true) =>
    unwrap<{ imported: number }>(http.post(`/connections/${connectionId}/databases/${database}/tables/${table}/import-csv`, {
      database, table, csvContent, hasHeader
    })),

  getErDiagram: (connectionId: string, database: string) =>
    unwrap<import('@/types/features').ErDiagramData & { tables: SchemaSummary['tables'] }>(
      http.get(`/connections/${connectionId}/databases/${database}/er-diagram`)
    ),
  compareSchemas: (connectionId: string, sourceDatabase: string, targetDatabase: string) =>
    unwrap<import('@/types/features').SchemaCompareResult>(
      http.get(`/connections/${connectionId}/schema-compare`, { params: { sourceDatabase, targetDatabase } })
    ),
  transferData: (connectionId: string, payload: {
    sourceDatabase: string; sourceTable: string; targetDatabase: string; targetTable: string; truncateTarget?: boolean
  }) => unwrap<{ transferred: number }>(http.post(`/connections/${connectionId}/data-transfer`, payload)),
  compareData: (connectionId: string, sourceDatabase: string, sourceTable: string, targetDatabase: string, targetTable: string) =>
    unwrap<import('@/types/features').DataCompareResult>(
      http.get(`/connections/${connectionId}/data-compare`, { params: { sourceDatabase, sourceTable, targetDatabase, targetTable } })
    )
}
