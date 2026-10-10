<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">后台 / 危机告警</p>
        <h2>敏感词危机告警</h2>
        <p class="sub">命中危机关键词的问答记录，及时标记处置</p>
      </div>
      <div class="head-actions">
        <div class="seg">
          <button class="seg-btn" :class="{ active: showHandled === '' }" @click="setFilter('')">全部</button>
          <button class="seg-btn" :class="{ active: showHandled === 'false' }" @click="setFilter('false')">待处理</button>
          <button class="seg-btn" :class="{ active: showHandled === 'true' }" @click="setFilter('true')">已处理</button>
        </div>
        <button class="btn ghost" :disabled="loading" @click="loadList">刷新</button>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="success" class="alert success">{{ success }}</p>

    <div class="list">
      <div v-if="loading" class="empty">加载中...</div>
      <div v-else-if="alerts.length === 0" class="empty">暂无危机告警记录</div>

      <article v-for="a in alerts" :key="a.id" class="item" :class="{ handled: a.handled }">
        <div class="item-top">
          <span class="level" :class="levelClass(a.level)">{{ a.level || 'HIGH' }}</span>
          <span class="q">{{ a.question || '-' }}</span>
        </div>
        <div class="meta">
          <span>关键词：<b class="kw">{{ parseKw(a.matchedKeywords) }}</b></span>
          <span>用户：{{ a.username || ('#' + (a.userId ?? '-')) }}</span>
          <span>{{ fmt(a.createdAt) }}</span>
          <span v-if="a.handled" class="handled-tag">已处置 · {{ a.handledBy || '-' }} {{ fmt(a.handledAt) }}</span>
        </div>
        <div class="item-foot">
          <button
            v-if="!a.handled"
            class="btn primary sm"
            :disabled="processing === a.id"
            @click="markHandled(a)"
          >{{ processing === a.id ? '处理中' : '标记已处置' }}</button>
          <span v-else class="done">✓ 已处置</span>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listCrisisAlerts, handleCrisisAlert } from '../../services/api.js'

const alerts = ref([])
const loading = ref(false)
const processing = ref(null)
const error = ref('')
const success = ref('')
const showHandled = ref('')

function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 19) : '-' }
function levelClass(l) {
  const s = (l || 'HIGH').toUpperCase()
  if (s === 'CRITICAL') return 'crit'
  if (s === 'MEDIUM') return 'med'
  if (s === 'LOW') return 'low'
  return 'high'
}
function parseKw(raw) {
  if (!raw) return '-'
  if (Array.isArray(raw)) return raw.join('、')
  try {
    const p = JSON.parse(raw)
    return Array.isArray(p) ? p.join('、') : String(raw)
  } catch {
    return String(raw)
  }
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (showHandled.value !== '') params.handled = showHandled.value
    const res = await listCrisisAlerts(params)
    alerts.value = Array.isArray(res) ? res : (res?.content ?? res?.records ?? res?.data ?? [])
  } catch (e) {
    error.value = '获取危机告警失败：' + (e?.message || e)
    alerts.value = []
  } finally {
    loading.value = false
  }
}

function setFilter(v) {
  showHandled.value = v
  loadList()
}

async function markHandled(a) {
  if (!confirm('确认将该危机事件标记为已处置？')) return
  processing.value = a.id
  error.value = ''
  success.value = ''
  try {
    await handleCrisisAlert(a.id)
    success.value = '已标记事件 #' + a.id + ' 为已处置'
    await loadList()
  } catch (e) {
    error.value = '处置失败：' + (e?.message || e)
  } finally {
    processing.value = null
  }
}

onMounted(loadList)
</script>

<style scoped>
.page { max-width: 900px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; }
.eyebrow { color: #7a8794; font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin: 0 0 6px; }
.page-head h2 { color: #2d3741; font-size: 24px; margin: 0; letter-spacing: -.01em; }
.sub { color: #8a96a3; font-size: 13px; margin: 5px 0 0; }
.head-actions { display: flex; gap: 10px; align-items: center; }

.seg { display: inline-flex; background: #f0f2f5; border: 1px solid #e4e8ee; border-radius: 9px; padding: 4px; }
.seg-btn { border: 0; background: transparent; color: #6b7785; padding: 6px 12px; border-radius: 7px; font-size: 12px; font-weight: 700; cursor: pointer; }
.seg-btn.active { background: #ffffff; color: #1f64da; box-shadow: 0 1px 3px rgba(40,56,74,.08); }

.alert { padding: 10px 14px; border-radius: 9px; font-size: 13px; margin: 0 0 14px; }
.alert.error { background: #fdeced; border: 1px solid #f4c4c8; color: #c0392b; }
.alert.success { background: #ecf7f0; border: 1px solid #c3e3cf; color: #1f7a45; }

.list { display: flex; flex-direction: column; gap: 12px; }
.empty { padding: 26px; text-align: center; color: #8a96a3; border-radius: 12px; background: #ffffff; border: 1px solid #e4e8ee; }
.item { background: #ffffff; border: 1px solid #e4e8ee; border-radius: 12px; padding: 16px 18px; border-left: 4px solid #d9534f; }
.item.handled { border-left-color: #1f7a45; opacity: .85; }
.item-top { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.level { font-size: 11px; font-weight: 700; padding: 3px 9px; border-radius: 99px; }
.level.crit { background: #fbe3e3; color: #b02a37; }
.level.high { background: #fdeced; color: #c0392b; }
.level.med { background: #fbf3df; color: #8a6d1a; }
.level.low { background: #e7eefb; color: #2f5bb0; }
.q { color: #2d3741; font-size: 15px; font-weight: 600; }
.meta { display: flex; flex-wrap: wrap; gap: 14px; color: #8a96a3; font-size: 12px; margin-bottom: 12px; }
.kw { color: #c0392b; }
.handled-tag { color: #1f7a45; }
.item-foot { display: flex; justify-content: flex-end; }
.done { color: #1f7a45; font-size: 13px; font-weight: 700; }

.btn { border: 0; border-radius: 9px; padding: 10px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .16s; }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn.primary { background: #2f6df0; color: #fff; }
.btn.ghost { background: #ffffff; color: #5b6775; border: 1px solid #d8dee6; }
.btn.ghost:hover:not(:disabled) { background: #f0f4f8; }
.btn.sm { padding: 8px 14px; font-size: 12px; }
</style>
