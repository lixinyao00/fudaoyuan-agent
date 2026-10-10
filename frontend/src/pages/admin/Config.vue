<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">后台 / 配置</p>
        <h2>系统配置管理</h2>
        <p class="sub">系统提示词、模型参数、关键词映射与危机词，保存后即时生效</p>
      </div>
      <button class="btn ghost" :disabled="loading" @click="loadConfigs">刷新</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="success" class="alert success">{{ success }}</p>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="groups.length === 0" class="empty glass">
      未获取到配置项。后端配置接口尚未就绪，或当前账号无权限。
    </div>

    <section v-for="grp in groups" :key="grp.name" class="group glass">
      <h3 class="group-title">{{ grp.name }}</h3>
      <div v-for="item in grp.items" :key="item.key" class="cfg">
        <div class="cfg-head">
          <span class="cfg-key mono">{{ item.key }}</span>
          <span v-if="item.description" class="cfg-desc">{{ item.description }}</span>
        </div>
        <textarea
          v-if="isMultiline(item)"
          v-model="item.value"
          rows="4"
          class="cfg-input area"
        ></textarea>
        <input
          v-else
          v-model="item.value"
          class="cfg-input"
          :type="inputType(item)"
        />
        <div class="cfg-foot">
          <button
            class="btn primary sm"
            :disabled="saving === item.key || item.value === item.original"
            @click="save(item)"
          >
            {{ saving === item.key ? '保存中' : '保存' }}
          </button>
          <span v-if="item.value !== item.original" class="dirty">有未保存修改</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listConfigs, updateConfig } from '../../services/api.js'

const configs = ref([])
const loading = ref(false)
const saving = ref(null)
const error = ref('')
const success = ref('')

const GROUP_LABELS = [
  { test: /prompt|system/i, name: '提示词' },
  { test: /llm|model|token|temperature|baseurl|url|api/i, name: '模型参数' },
  { test: /embed/i, name: '向量化' },
  { test: /keyword|映射/i, name: '关键词映射' },
  { test: /crisis|危机/i, name: '危机告警' }
]

const groups = computed(() => {
  const map = new Map()
  for (const item of configs.value) {
    const label = GROUP_LABELS.find(g => g.test.test(item.key))?.name || '其他'
    if (!map.has(label)) map.set(label, [])
    map.get(label).push(item)
  }
  const order = ['提示词', '模型参数', '向量化', '关键词映射', '危机告警', '其他']
  return [...map.entries()]
    .map(([name, items]) => ({ name, items }))
    .sort((a, b) => order.indexOf(a.name) - order.indexOf(b.name))
})

function isMultiline(item) {
  if (item.type && /text|prompt|json|keyword|map/i.test(item.type)) return true
  return typeof item.value === 'string' && (item.value.length > 60 || item.value.includes('\n'))
}
function inputType(item) {
  if (item.type && /number|int/i.test(item.type)) return 'number'
  return 'text'
}

function normalize(raw) {
  // 兼容数组[{configKey,configValue,configType,description}] 或对象map
  const toList = (key, value, type, desc) => ({
    key, value: value == null ? '' : String(value), original: value == null ? '' : String(value), type, description: desc
  })
  if (Array.isArray(raw)) {
    return raw.map(r => toList(
      r.configKey ?? r.key,
      r.configValue ?? r.value,
      r.configType ?? r.type,
      r.description ?? r.desc
    ))
  }
  if (raw && typeof raw === 'object') {
    return Object.entries(raw).map(([k, v]) => toList(k, v, typeof v === 'number' ? 'number' : 'string', ''))
  }
  return []
}

async function loadConfigs() {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const res = await listConfigs()
    const data = Array.isArray(res) ? res : (res?.data ?? res?.configs ?? res)
    configs.value = normalize(data)
  } catch (e) {
    error.value = '获取配置失败：' + (e?.message || e)
    configs.value = []
  } finally {
    loading.value = false
  }
}

async function save(item) {
  saving.value = item.key
  error.value = ''
  success.value = ''
  try {
    await updateConfig(item.key, item.value)
    item.original = item.value
    success.value = '已保存：' + item.key
  } catch (e) {
    error.value = '保存失败：' + (e?.message || e)
  } finally {
    saving.value = null
  }
}

onMounted(loadConfigs)
</script>

<style scoped>
.page { max-width: 900px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; }
.eyebrow { color: #7a8794; font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin: 0 0 6px; }
.page-head h2 { color: #2d3741; font-size: 24px; margin: 0; letter-spacing: -.01em; }
.sub { color: #8a96a3; font-size: 13px; margin: 5px 0 0; }

.alert { padding: 10px 14px; border-radius: 9px; font-size: 13px; margin: 0 0 14px; }
.alert.error { background: #fdeced; border: 1px solid #f4c4c8; color: #c0392b; }
.alert.success { background: #ecf7f0; border: 1px solid #c3e3cf; color: #1f7a45; }
.loading, .empty { padding: 22px; text-align: center; color: #8a96a3; border-radius: 12px; background: #ffffff; border: 1px solid #e4e8ee; }

.group { padding: 18px 20px; border-radius: 14px; background: #ffffff; border: 1px solid #e4e8ee; margin-bottom: 16px; }
.group-title { color: #2d3741; font-size: 15px; margin: 0 0 14px; font-weight: 700; letter-spacing: -.01em; }

.cfg { padding: 12px 0; border-top: 1px solid #edf0f4; }
.cfg:first-of-type { border-top: 0; padding-top: 0; }
.cfg-head { display: flex; flex-direction: column; gap: 2px; margin-bottom: 8px; }
.cfg-key { color: #2d3741; font-size: 13px; font-weight: 600; }
.cfg-desc { color: #8a96a3; font-size: 12px; }
.cfg-input { width: 100%; padding: 10px 12px; border-radius: 9px; border: 1px solid #d8dee6; background: #fbfcfd; color: #2d3741; font-size: 13px; font-family: inherit; outline: none; transition: border-color .16s, box-shadow .16s; }
.cfg-input:focus { border-color: #2f6df0; box-shadow: 0 0 0 3px rgba(47,109,240,.12); }
.cfg-input.area { resize: vertical; line-height: 1.6; font-family: ui-monospace, Menlo, monospace; }
.cfg-foot { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.dirty { color: #8a6d1a; font-size: 12px; }

.mono { font-family: ui-monospace, Menlo, monospace; }
.btn { border: 0; border-radius: 9px; padding: 10px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .16s; }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn.primary { background: #2f6df0; color: #fff; }
.btn.ghost { background: #ffffff; color: #5b6775; border: 1px solid #d8dee6; }
.btn.ghost:hover:not(:disabled) { background: #f0f4f8; }
.btn.sm { padding: 8px 14px; font-size: 12px; }
</style>
