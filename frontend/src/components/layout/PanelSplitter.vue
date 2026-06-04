<template>
  <div
    class="panel-splitter"
    :class="[
      direction === 'horizontal' ? 'panel-splitter-h' : 'panel-splitter-v',
      { active: dragging }
    ]"
    role="separator"
    :aria-orientation="direction === 'horizontal' ? 'horizontal' : 'vertical'"
    @mousedown="onMouseDown"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  direction: 'horizontal' | 'vertical'
  invert?: boolean
}>(), {
  invert: false
})

const emit = defineEmits<{
  resize: [delta: number]
  'resize-start': []
  'resize-end': []
}>()

const dragging = ref(false)

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  e.preventDefault()

  dragging.value = true
  emit('resize-start')

  const start = props.direction === 'horizontal' ? e.clientY : e.clientX
  const cursor = props.direction === 'horizontal' ? 'row-resize' : 'col-resize'
  document.body.style.cursor = cursor
  document.body.style.userSelect = 'none'

  const onMove = (ev: MouseEvent) => {
    const current = props.direction === 'horizontal' ? ev.clientY : ev.clientX
    let delta = current - start
    if (props.invert) delta = -delta
    emit('resize', delta)
  }

  const onUp = () => {
    dragging.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    emit('resize-end')
  }

  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
</script>
