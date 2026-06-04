<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog">
      <div class="dialog-header">
        <h2>{{ title }}</h2>
        <button class="close-btn" @click="close">×</button>
      </div>
      <div class="dialog-body">
        <slot />
      </div>
      <div class="dialog-footer">
        <button class="btn btn-primary" @click="close">确定</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  title: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
}

.dialog {
  width: 480px;
  max-width: calc(100vw - 32px);
  background: var(--bg-primary);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-shadow: var(--shadow-lg);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
  border-radius: 6px 6px 0 0;
}

.dialog-header h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 20px;
  line-height: 1;
  padding: 0 4px;
}

.close-btn:hover {
  color: var(--text-primary);
}

.dialog-body {
  padding: 16px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
  border-radius: 0 0 6px 6px;
}
</style>
