import type { AppSettings } from '@/types/features'

const STORAGE_KEY = 'opendb-settings'

const defaults: AppSettings = {
  editorFontSize: 13,
  queryLimit: 500,
  confirmDestructive: true,
  autoSaveProfile: true,
  backgroundEnabled: false,
  backgroundFit: 'cover',
  backgroundPanelOpacity: 88,
  backgroundBlur: 10
}

export function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? { ...defaults, ...(JSON.parse(raw) as AppSettings) } : { ...defaults }
  } catch {
    return { ...defaults }
  }
}

export function saveSettings(settings: AppSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
}
