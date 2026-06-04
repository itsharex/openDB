<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog">
      <div class="dialog-header">
        <h2>新建数据库</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <form class="dialog-body" @submit.prevent="submit">
        <label>数据库名称<input v-model="name" required placeholder="my_database" /></label>
        <div class="grid-2">
          <label>字符集<input v-model="charset" /></label>
          <label>排序规则<input v-model="collation" /></label>
        </div>
        <div v-if="name.trim()" class="tips">
          <div v-for="(tip, i) in namingTips" :key="i" class="tip" :class="tip.level">
            {{ tip.message }}
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" @click="close">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting || !nameValid">
            {{ submitting ? '创建中...' : '创建' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'

const props = defineProps<{ modelValue: boolean; connectionId: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [string] }>()

const name = ref('')
const charset = ref('utf8mb4')
const collation = ref('utf8mb4_unicode_ci')
const submitting = ref(false)
const error = ref('')

const namingTips = computed(() => {
  const n = name.value.trim()
  const tips: Array<{ level: 'good' | 'warn' | 'info'; message: string }> = []
  if (!n) return tips
  if (/^[a-z][a-z0-9_]*$/i.test(n)) {
    tips.push({ level: 'good', message: '命名格式符合常见规范（字母开头，仅字母数字下划线）。' })
  } else {
    tips.push({ level: 'warn', message: '建议使用字母开头，仅包含字母、数字和下划线。' })
  }
  if (n.length > 64) tips.push({ level: 'warn', message: '名称较长，部分数据库对库名长度有限制。' })
  if (['mysql', 'information_schema', 'performance_schema', 'sys'].includes(n.toLowerCase())) {
    tips.push({ level: 'warn', message: '该名称可能与系统库冲突。' })
  }
  if (charset.value === 'utf8mb4') {
    tips.push({ level: 'info', message: 'utf8mb4 字符集支持完整 Unicode（含 emoji）。' })
  }
  return tips
})

const nameValid = computed(() => !!name.value.trim() && namingTips.value.every(t => t.level !== 'warn' || !/冲突|较长/.test(t.message)))

watch(() => props.modelValue, v => { if (v) { name.value = ''; error.value = '' } })

function close() { emit('update:modelValue', false) }

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    await api.createDatabase(props.connectionId, {
      name: name.value,
      charset: charset.value,
      collation: collation.value
    })
    emit('created', name.value)
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog { width: 440px; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.tips { display: flex; flex-direction: column; gap: 4px; }
.tip { font-size: 11px; padding: 6px 8px; border-radius: var(--radius); background: var(--bg-secondary); border-left: 3px solid var(--border); }
.tip.good { border-left-color: var(--success); }
.tip.warn { border-left-color: var(--warning); }
.tip.info { border-left-color: var(--accent); }
.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; }
.error { margin: 0; color: var(--danger); font-size: 12px; }
</style>
