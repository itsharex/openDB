<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog">
      <div class="dialog-header">
        <h2>选项</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <div class="dialog-body scrollbar">
        <section class="section">
          <h3>使用统计</h3>
          <div class="stats-grid">
            <div class="stat"><span>历史查询</span><b>{{ usageStats.total }}</b></div>
            <div class="stat"><span>成功率</span><b>{{ usageStats.successRate.toFixed(0) }}%</b></div>
            <div class="stat"><span>平均耗时</span><b>{{ usageStats.avgDurationMs.toFixed(0) }}ms</b></div>
            <div class="stat"><span>慢查询</span><b>{{ usageStats.slowCount }}</b></div>
          </div>
          <p v-if="usageTip" class="usage-tip">{{ usageTip }}</p>
        </section>

        <section class="section">
          <h3>编辑器</h3>
          <label>编辑器字号<input v-model.number="local.editorFontSize" type="number" min="11" max="20" /></label>
          <label>默认查询行数限制<input v-model.number="local.queryLimit" type="number" min="100" max="10000" step="100" /></label>
          <label class="checkbox"><input v-model="local.confirmDestructive" type="checkbox" /> 执行危险操作前确认 (DROP/TRUNCATE/DELETE)</label>
          <label class="checkbox"><input v-model="local.autoSaveProfile" type="checkbox" /> 连接成功后自动保存连接配置（已默认启用本地持久化）</label>
        </section>

        <section class="section">
          <h3>外观 · 背景图片</h3>
          <div class="bg-preview" :class="{ empty: !previewImage }">
            <img v-if="previewImage" :src="previewImage" alt="背景预览" />
            <span v-else>尚未上传背景图</span>
          </div>
          <div class="bg-actions">
            <button type="button" class="btn btn-secondary btn-sm" @click="pickImage">上传图片</button>
            <button type="button" class="btn btn-ghost btn-sm" :disabled="!previewImage" @click="clearImage">清除图片</button>
          </div>
          <p v-if="imageError" class="error-text">{{ imageError }}</p>
          <label class="checkbox"><input v-model="local.backgroundEnabled" type="checkbox" :disabled="!previewImage" /> 启用自定义背景</label>
          <label>
            显示方式
            <select v-model="local.backgroundFit" :disabled="!previewImage">
              <option value="cover">铺满 (cover)</option>
              <option value="contain">完整显示 (contain)</option>
              <option value="tile">平铺 (tile)</option>
            </select>
          </label>
          <label>
            面板透明度 {{ local.backgroundPanelOpacity }}%
            <input v-model.number="local.backgroundPanelOpacity" type="range" min="50" max="100" step="1" :disabled="!previewImage" />
          </label>
          <label>
            毛玻璃模糊 {{ local.backgroundBlur }}px
            <input v-model.number="local.backgroundBlur" type="range" min="0" max="20" step="1" :disabled="!previewImage" />
          </label>
        </section>
      </div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" @click="close">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
      <input ref="imageInputRef" type="file" accept="image/*" hidden @change="onImageSelected" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { loadSettings, saveSettings } from '@/composables/useAppSettings'
import { loadBackgroundImage, readImageFile, saveBackgroundImage } from '@/composables/useBackgroundTheme'
import { analyzeQueryHistory } from '@/utils/queryHistoryAnalytics'
import { loadQueryHistory } from '@/composables/useQueryHistory'
import type { AppSettings } from '@/types/features'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [AppSettings] }>()

const local = reactive(loadSettings())
const previewImage = ref<string | null>(loadBackgroundImage())
const imageError = ref('')
const imageInputRef = ref<HTMLInputElement>()
const usageStats = reactive({ total: 0, successRate: 0, avgDurationMs: 0, slowCount: 0 })
const usageTip = ref('')

function refreshUsageStats() {
  const stats = analyzeQueryHistory(loadQueryHistory())
  usageStats.total = stats.total
  usageStats.successRate = stats.successRate
  usageStats.avgDurationMs = stats.avgDurationMs
  usageStats.slowCount = stats.slowQueries.length
  if (stats.total === 0) {
    usageTip.value = '执行 SQL 后将在此显示使用统计。'
  } else if (stats.slowQueries.length > 0) {
    usageTip.value = `检测到 ${stats.slowQueries.length} 条慢查询，可在「查询历史 → 统计分析」查看详情。`
  } else if (stats.successRate >= 95) {
    usageTip.value = '查询执行稳定，成功率良好。'
  } else {
    usageTip.value = '部分查询失败，建议检查 SQL 语法或连接权限。'
  }
}

watch(() => props.modelValue, v => {
  if (v) {
    Object.assign(local, loadSettings())
    previewImage.value = loadBackgroundImage()
    imageError.value = ''
    refreshUsageStats()
  }
})

function close() {
  emit('update:modelValue', false)
}

function pickImage() {
  imageInputRef.value?.click()
}

async function onImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  imageError.value = ''
  try {
    const dataUrl = await readImageFile(file)
    previewImage.value = dataUrl
    saveBackgroundImage(dataUrl)
    local.backgroundEnabled = true
  } catch (e) {
    imageError.value = e instanceof Error ? e.message : '上传失败'
  }
}

function clearImage() {
  previewImage.value = null
  saveBackgroundImage(null)
  local.backgroundEnabled = false
  imageError.value = ''
}

function save() {
  if (local.backgroundEnabled && !previewImage.value) {
    local.backgroundEnabled = false
  }
  const settings: AppSettings = { ...local }
  saveSettings(settings)
  emit('saved', settings)
  close()
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog { width: 460px; max-height: calc(100vh - 48px); display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.stats-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.stat { border: 1px solid var(--border); border-radius: var(--radius); padding: 8px 10px; background: var(--bg-secondary); font-size: 11px; }
.stat span { display: block; color: var(--text-muted); margin-bottom: 2px; }
.stat b { color: var(--accent); font-size: 16px; }
.usage-tip { margin: 0; font-size: 11px; color: var(--text-muted); padding: 8px; background: var(--bg-secondary); border-radius: var(--radius); border: 1px solid var(--border); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 16px; overflow: auto; }
.section { display: flex; flex-direction: column; gap: 12px; }
.section h3 { margin: 0; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.04em; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.checkbox { flex-direction: row; align-items: center; gap: 8px; color: var(--text-primary); }
.bg-preview {
  height: 120px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
}
.bg-preview img { width: 100%; height: 100%; object-fit: cover; }
.bg-preview.empty { color: var(--text-muted); font-size: 12px; }
.bg-actions { display: flex; gap: 8px; }
.error-text { margin: 0; font-size: 12px; color: var(--danger); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
</style>
