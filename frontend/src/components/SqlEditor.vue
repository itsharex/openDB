<template>
  <div class="sql-editor">
    <div class="editor-layout">
      <div class="editor-actions">
        <button
          type="button"
          class="run-btn primary"
          :disabled="!hasConnection"
          title="运行 SQL (Ctrl+Enter)"
          @click="emitRun('all')"
        >
          <span class="run-icon">▶</span>
          <span class="run-label">运行</span>
        </button>
        <button
          type="button"
          class="run-btn"
          :disabled="!hasConnection"
          title="运行选中 SQL (Ctrl+Shift+Enter)"
          @click="emitRun('selection')"
        >
          <span class="run-icon">▶</span>
          <span class="run-label">选中</span>
        </button>
      </div>
      <div ref="editorHost" class="editor-host"></div>
    </div>
    <div class="editor-footer">
      <div class="shortcut-hints">
        <span v-for="item in editorShortcutHints" :key="item.keys" class="shortcut-item">
          <kbd>{{ item.keys }}</kbd> {{ item.label }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { autocompletion, completionKeymap } from '@codemirror/autocomplete'
import { defaultKeymap, indentWithTab, redo, undo } from '@codemirror/commands'
import { sql } from '@codemirror/lang-sql'
import { syntaxHighlighting, defaultHighlightStyle } from '@codemirror/language'
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLine, drawSelection } from '@codemirror/view'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { SchemaSummary } from '@/types'
import { editorShortcutHints } from '@/config/sqlQuickCommands'
import { buildCodeMirrorSchema, createSchemaCompletion } from '@/utils/sqlCompletion'
import { getCodeMirrorDialect } from '@/utils/sqlDialect'

const props = defineProps<{
  modelValue: string
  databases?: string[]
  schema?: SchemaSummary | null
  hasConnection?: boolean
  databaseType?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  run: [mode: 'all' | 'selection']
}>()

const editorHost = ref<HTMLElement | null>(null)
let view: EditorView | null = null
const languageCompartment = new Compartment()

function emitRun(mode: 'all' | 'selection') {
  emit('run', mode)
}

function createLanguageExtension() {
  const schemaSpec = buildCodeMirrorSchema(props.schema ?? null, props.databases ?? [])
  return [
    sql({
      dialect: getCodeMirrorDialect(props.databaseType),
      upperCaseKeywords: true,
      schema: schemaSpec
    }),
    autocompletion({
      override: [
        createSchemaCompletion(
          () => props.schema ?? null,
          () => props.databases ?? []
        )
      ],
      activateOnTyping: true,
      maxRenderedOptions: 30
    })
  ]
}

function createEditor() {
  if (!editorHost.value) {
    return
  }

  view = new EditorView({
    parent: editorHost.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        drawSelection(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        languageCompartment.of(createLanguageExtension()),
        keymap.of([
          ...defaultKeymap,
          ...completionKeymap,
          indentWithTab,
          {
            key: 'Mod-Enter',
            run: () => {
              emitRun('all')
              return true
            }
          },
          {
            key: 'Mod-Shift-Enter',
            run: () => {
              emitRun('selection')
              return true
            }
          }
        ]),
        EditorView.updateListener.of(update => {
          if (update.docChanged) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
        EditorView.theme({
          '&': {
            height: '100%',
            fontSize: '13px'
          },
          '.cm-scroller': {
            fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
            lineHeight: '1.6'
          },
          '&.cm-focused': {
            outline: 'none'
          },
          '.cm-gutters': {
            backgroundColor: 'transparent',
            borderRight: '1px solid var(--border)',
            color: 'var(--text-secondary)'
          },
          '.cm-activeLine': {
            backgroundColor: 'rgba(51, 112, 255, 0.05)'
          },
          '.cm-content': {
            caretColor: 'var(--accent)'
          },
          '.cm-cursor': {
            borderLeftColor: 'var(--accent)'
          },
          '.cm-tooltip-autocomplete': {
            backgroundColor: 'var(--bg-secondary)',
            border: '1px solid var(--border)',
            color: 'var(--text-primary)'
          },
          '.cm-completionLabel': {
            fontFamily: "'JetBrains Mono', 'Fira Code', monospace"
          }
        })
      ]
    })
  })
}

function getSelectedText() {
  if (!view) return ''
  const { from, to } = view.state.selection.main
  if (from === to) return ''
  return view.state.sliceDoc(from, to)
}

function selectAll() {
  if (!view) return
  view.dispatch({ selection: { anchor: 0, head: view.state.doc.length } })
  view.focus()
}

function replaceText(find: string, replace: string, all = false) {
  if (!view || !find) return 0
  const doc = view.state.doc.toString()
  if (all) {
    const count = doc.split(find).length - 1
    if (count <= 0) return 0
    const next = doc.split(find).join(replace)
    view.dispatch({ changes: { from: 0, to: doc.length, insert: next } })
    return count
  }
  const index = doc.indexOf(find)
  if (index < 0) return 0
  view.dispatch({
    changes: { from: index, to: index + find.length, insert: replace },
    selection: { anchor: index + replace.length }
  })
  view.focus()
  return 1
}

function findNext(find: string) {
  if (!view || !find) return false
  const doc = view.state.doc.toString()
  const { to } = view.state.selection.main
  let index = doc.indexOf(find, to)
  if (index < 0) index = doc.indexOf(find)
  if (index < 0) return false
  view.dispatch({ selection: { anchor: index, head: index + find.length } })
  view.focus()
  return true
}

function editorUndo() {
  if (!view) return
  undo({ state: view.state, dispatch: view.dispatch })
}

function editorRedo() {
  if (!view) return
  redo({ state: view.state, dispatch: view.dispatch })
}

function insertText(text: string, replaceAll = false) {
  if (!view) return
  const doc = view.state.doc
  if (replaceAll) {
    view.dispatch({
      changes: { from: 0, to: doc.length, insert: text },
      selection: { anchor: text.length }
    })
  } else {
    const { from, to } = view.state.selection.main
    const prefix = from > 0 && doc.sliceString(from - 1, from) !== '\n' ? '\n' : ''
    const insert = `${prefix}${text}`
    view.dispatch({
      changes: { from, to, insert },
      selection: { anchor: from + insert.length }
    })
  }
  view.focus()
}

defineExpose({ getSelectedText, selectAll, replaceText, findNext, undo: editorUndo, redo: editorRedo, insertText })

onMounted(createEditor)

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})

watch(
  () => props.modelValue,
  value => {
    if (!view) {
      return
    }
    const current = view.state.doc.toString()
    if (value !== current) {
      view.dispatch({
        changes: { from: 0, to: current.length, insert: value }
      })
    }
  }
)

watch(
  () => [props.schema, props.databases, props.databaseType] as const,
  () => {
    if (!view) {
      return
    }
    view.dispatch({
      effects: languageCompartment.reconfigure(createLanguageExtension())
    })
  },
  { deep: true }
)
</script>

<style scoped>
.sql-editor {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.editor-actions {
  width: 52px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 6px;
  border-right: 1px solid var(--border);
  background: var(--bg-secondary);
}

.run-btn {
  width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 6px 4px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.2;
}

.run-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}

.run-btn.primary {
  color: var(--accent);
  border-color: #b3ccff;
  background: var(--accent-light);
}

.run-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.run-icon {
  font-size: 12px;
  line-height: 1;
}

.run-label {
  font-size: 10px;
  font-weight: 600;
}

.editor-host {
  flex: 1;
  min-height: 180px;
  min-width: 0;
  overflow: hidden;
}

.editor-footer {
  padding: 4px 10px;
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.shortcut-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

.shortcut-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--text-muted);
}

.shortcut-item kbd {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-strong);
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 10px;
  font-family: inherit;
  color: var(--text-secondary);
}
</style>
