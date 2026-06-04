import type { QueryHistoryItem } from '@/types/features'

const STORAGE_KEY = 'opendb-query-history'
const MAX_ITEMS = 100

export function loadQueryHistory(): QueryHistoryItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as QueryHistoryItem[]) : []
  } catch {
    return []
  }
}

export function saveQueryHistory(items: QueryHistoryItem[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_ITEMS)))
}

export function addQueryHistory(item: Omit<QueryHistoryItem, 'id'>) {
  const items = loadQueryHistory()
  items.unshift({ ...item, id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}` })
  saveQueryHistory(items)
  return items
}

export function clearQueryHistory() {
  localStorage.removeItem(STORAGE_KEY)
}
