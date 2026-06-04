import type { AppSettings } from '@/types/features'

const BG_IMAGE_KEY = 'opendb-bg-image'
const BG_LAYER_ID = 'opendb-bg-layer'
const MAX_IMAGE_BYTES = 3 * 1024 * 1024

export function loadBackgroundImage(): string | null {
  try {
    return localStorage.getItem(BG_IMAGE_KEY)
  } catch {
    return null
  }
}

export function saveBackgroundImage(dataUrl: string | null) {
  if (dataUrl) {
    localStorage.setItem(BG_IMAGE_KEY, dataUrl)
  } else {
    localStorage.removeItem(BG_IMAGE_KEY)
  }
}

function ensureBackgroundLayer(): HTMLDivElement {
  let layer = document.getElementById(BG_LAYER_ID) as HTMLDivElement | null
  if (!layer) {
    layer = document.createElement('div')
    layer.id = BG_LAYER_ID
    document.body.prepend(layer)
  }
  return layer
}

function removeBackgroundLayer() {
  document.getElementById(BG_LAYER_ID)?.remove()
}

export function applyBackgroundTheme(settings: AppSettings) {
  const root = document.documentElement
  const image = settings.backgroundEnabled ? loadBackgroundImage() : null

  if (!settings.backgroundEnabled || !image) {
    root.classList.remove('has-custom-bg')
    root.style.removeProperty('--custom-panel-opacity')
    root.style.removeProperty('--custom-bg-blur')
    removeBackgroundLayer()
    return
  }

  root.classList.add('has-custom-bg')
  root.style.setProperty('--custom-panel-opacity', String(settings.backgroundPanelOpacity / 100))
  root.style.setProperty('--custom-bg-blur', `${settings.backgroundBlur}px`)

  const layer = ensureBackgroundLayer()
  layer.className = settings.backgroundFit === 'tile' ? 'tile' : settings.backgroundFit

  if (settings.backgroundFit === 'tile') {
    layer.style.backgroundImage = `url("${image.replace(/"/g, '%22')}")`
    layer.innerHTML = ''
    return
  }

  layer.style.backgroundImage = 'none'
  let img = layer.querySelector('img')
  if (!img) {
    layer.innerHTML = ''
    img = document.createElement('img')
    img.alt = ''
    img.draggable = false
    layer.appendChild(img)
  }
  if (img.src !== image) {
    img.src = image
  }
}

export function readImageFile(file: File): Promise<string> {
  if (!file.type.startsWith('image/')) {
    return Promise.reject(new Error('请选择 JPG、PNG、GIF 或 WebP 图片'))
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return Promise.reject(new Error('图片不能超过 3MB'))
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}
