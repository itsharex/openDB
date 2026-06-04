<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog">
      <div class="dialog-header">
        <h2>查找和替换</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <div class="dialog-body">
        <label>查找<input ref="findInput" v-model="findText" @keydown.enter="findNext" /></label>
        <label>替换为<input v-model="replaceText" @keydown.enter="replaceOne" /></label>
      </div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" @click="findNext">查找下一个</button>
        <button class="btn btn-secondary" @click="replaceOne">替换</button>
        <button class="btn btn-primary" @click="replaceAll">全部替换</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  find: [text: string]
  replace: [find: string, replace: string, all: boolean]
}>()

const findText = ref('')
const replaceText = ref('')
const findInput = ref<HTMLInputElement | null>(null)

watch(() => props.modelValue, async v => {
  if (v) {
    await nextTick()
    findInput.value?.focus()
  }
})

function close() { emit('update:modelValue', false) }
function findNext() { emit('find', findText.value) }
function replaceOne() { emit('replace', findText.value, replaceText.value, false) }
function replaceAll() { emit('replace', findText.value, replaceText.value, true) }
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog { width: 400px; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; color: var(--text-muted); }
.dialog-body { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border); background: var(--bg-secondary); }
</style>
