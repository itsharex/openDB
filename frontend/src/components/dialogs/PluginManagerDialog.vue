<template>
  <div v-if="modelValue" class="dialog-overlay" @click.self="close">
    <div class="dialog wide">
      <div class="dialog-header"><h2>插件管理</h2><button class="close-btn" @click="close">×</button></div>
      <div class="dialog-body">
        <div class="summary-bar">
          <div class="summary-item"><span>已启用</span><b>{{ enabledCount }}/{{ plugins.length }}</b></div>
          <div class="summary-item"><span>数据库驱动</span><b>{{ driverCount }}</b></div>
          <div class="summary-item"><span>智能分析</span><b>{{ analyticsFeatures.length }}</b></div>
        </div>

        <div class="feature-grid">
          <div v-for="feat in analyticsFeatures" :key="feat.id" class="feature-card">
            <strong>{{ feat.name }}</strong>
            <p>{{ feat.desc }}</p>
          </div>
        </div>

        <div v-for="plugin in plugins" :key="plugin.id" class="plugin-item">
          <div class="plugin-info">
            <strong>{{ plugin.name }}</strong>
            <span class="version">v{{ plugin.version }}</span>
            <span class="category">{{ plugin.category }}</span>
            <p>{{ plugin.description }}</p>
            <div class="cap-tags">
              <span v-for="cap in plugin.capabilities" :key="cap" class="cap">{{ cap }}</span>
            </div>
          </div>
          <span class="status" :class="{ enabled: plugin.enabled }">{{ plugin.enabled ? '已启用' : '未启用' }}</span>
        </div>
        <p class="hint">openDB 内置扩展架构，智能分析能力覆盖查询结果、表结构、CSV、连接、ER 图等模块。</p>
      </div>
      <div class="dialog-footer"><button class="btn btn-primary" @click="close">关闭</button></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const plugins = [
  { id: 'mysql', name: 'MySQL 驱动', version: '1.0', category: '驱动', description: 'MySQL 连接、查询、DDL 管理', enabled: true, capabilities: ['连接', 'DDL', 'EXPLAIN'] },
  { id: 'postgresql', name: 'PostgreSQL 驱动', version: '1.0', category: '驱动', description: 'PostgreSQL 连接、查询、对象浏览', enabled: true, capabilities: ['连接', 'Schema', '查询'] },
  { id: 'oracle', name: 'Oracle 驱动', version: '1.0', category: '驱动', description: 'Oracle 连接、Schema 浏览、SQL 执行', enabled: true, capabilities: ['连接', 'Schema'] },
  { id: 'h2', name: 'H2 驱动', version: '1.0', category: '驱动', description: 'H2 嵌入式/远程连接支持', enabled: true, capabilities: ['连接', '内存库'] },
  { id: 'ai', name: 'AI 助手', version: '1.0', category: '智能', description: 'SQL 生成、解释、优化', enabled: true, capabilities: ['流式对话', 'SQL 提取'] },
  { id: 'analytics', name: '智能分析套件', version: '1.1', category: '智能', description: '结果统计、Schema 健康、CSV 推断、连接测速', enabled: true, capabilities: ['结果分析', 'EXPLAIN', 'CSV'] },
  { id: 'er', name: 'ER 图', version: '1.0', category: '可视化', description: '数据库关系可视化 + Schema 洞察', enabled: true, capabilities: ['ER 图', '枢纽分析'] },
  { id: 'designer', name: '表设计器', version: '1.0', category: '设计', description: '可视化建表 + 设计健康度评分', enabled: true, capabilities: ['DDL 生成', '健康评分'] }
]

const analyticsFeatures = [
  { id: 'result', name: '查询结果分析', desc: '列画像、分布、相关性、分组、选区分析' },
  { id: 'explain', name: '执行计划分析', desc: '全表扫描、filesort、temporary 检测' },
  { id: 'schema', name: '表结构健康', desc: '主键/索引/可空列洞察与优化建议' },
  { id: 'csv', name: 'CSV 类型推断', desc: '自动推断 SQL 类型与建表建议' },
  { id: 'conn', name: '连接测速', desc: '批量延迟检测与质量评级' },
  { id: 'history', name: '查询历史统计', desc: '成功率、慢查询、关键词分析' }
]

const enabledCount = computed(() => plugins.filter(p => p.enabled).length)
const driverCount = computed(() => plugins.filter(p => p.category === '驱动').length)

function close() { emit('update:modelValue', false) }
</script>

<style scoped>
.dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.dialog.wide { width: 580px; max-height: 85vh; display: flex; flex-direction: column; background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 6px; box-shadow: var(--shadow-lg); }
.dialog-header { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.dialog-header h2 { margin: 0; font-size: 14px; }
.close-btn { border: none; background: transparent; font-size: 20px; }
.dialog-body { padding: 16px; overflow: auto; flex: 1; }
.summary-bar { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; }
.summary-item { text-align: center; border: 1px solid var(--border); border-radius: var(--radius); padding: 8px; background: var(--bg-secondary); font-size: 11px; }
.summary-item span { display: block; color: var(--text-muted); margin-bottom: 2px; }
.summary-item b { color: var(--accent); font-size: 16px; }
.feature-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-bottom: 12px; }
.feature-card { border: 1px solid var(--border); border-radius: var(--radius); padding: 8px 10px; background: var(--accent-light); font-size: 11px; }
.feature-card strong { display: block; margin-bottom: 2px; color: var(--accent); }
.feature-card p { margin: 0; color: var(--text-secondary); }
.plugin-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; padding: 10px; border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 8px; }
.plugin-info p { margin: 4px 0; font-size: 11px; color: var(--text-muted); }
.version { font-size: 10px; color: var(--text-muted); margin-left: 6px; }
.category { font-size: 10px; color: var(--accent); margin-left: 6px; }
.cap-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.cap { font-size: 10px; padding: 1px 6px; border-radius: 999px; background: var(--bg-tertiary); color: var(--text-secondary); }
.status { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: var(--bg-tertiary); color: var(--text-muted); flex-shrink: 0; }
.status.enabled { background: var(--success-bg); color: #1a7f37; }
.hint { font-size: 11px; color: var(--text-muted); margin-top: 8px; }
.dialog-footer { display: flex; justify-content: flex-end; padding: 10px 16px; border-top: 1px solid var(--border); flex-shrink: 0; }
</style>
