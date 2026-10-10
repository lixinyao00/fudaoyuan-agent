<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">后台 / 监控</p>
        <h2>问答效果监控</h2>
        <p class="sub">查看问答日志、评分与检索耗时，筛选低分或危机记录</p>
      </div>
      <button class="btn ghost" :disabled="loading" @click="loadAll">刷新</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>

    <!-- 统计卡 -->
    <div class="cards">
      <div class="card glass"><span class="c-val">{{ stats.total ?? '-' }}</span><span class="c-label">总问答数</span></div>
      <div class="card glass"><span class="c-val">{{ fmtNum(stats.avgLatencyMs) }}</span><span class="c-label">平均耗时(ms)</span></div>
      <div class="card glass"><span class="c-val">{{ fmtPct(stats.positiveRate ?? stats.goodRate) }}</span><span class="c-label">好评率</span></div>
      <div class="card glass danger"><span class="c-val">{{ stats.crisisCount ?? '-' }}</span><span class="c-label">危机数</span></div>
    </div>

    <!-- 筛选 -->
    <div class="filters glass">
      <select v-model="filter.feedback" @change="reload">
        <option value="">全部评分</option>
        <option value="5">5 分好评</option>
        <option value="1">1 分差评</option>
        <option value="null">未评分</option>
      </select>
      <select v-model="filter.crisis" @change="reload">
        <option value="">全部记录</option>
        <option value="true">含危机标记</option>
      </select>
      <input v-model.trim="filter.keyword" placeholder="搜索问题关键字" @keyup.enter="reload" />
      <button class="btn primary sm" :disabled="loading" @click="reload">查询</button>
    </div>

    <!-- 列表 -->
    <div class="table glass">
      <div class="row head">
        <span>时间</span><span>用户</span><span>问题</span><span>评分</span><span>耗时</span><span>危机</span><span>操作</span>
      </div>
      <div v-if="loading" class="row empty">加载中...</div>
      <div v-else-if="logs.length === 0" class="row empty">暂无日志</div>
      <div v-for="l in logs" :key="l.id" class="row">
        <span class="mono small">{{ fmt(l.createdAt) }}</span>
        <span>{{ l.username || l.userName || ('#' + (l.userId ?? '-')) }}</span>
        <span class="q">{{ truncate(l.question) }}</span>
        <span>{{ l.feedback ?? '-' }}</span>
        <span class="mono">{{ l.latencyMs ?? '-' }}ms</span>
        <span><span v-if="l.crisis || l.hasCrisis" class="badge bad">危机</span><span v-else>-</span></span>
        <span><button class="btn ghost sm" @click="openDetail(l)">详情</button></span>
      </div>
    </div>

    <div class="pager" v-if="total > 0">
      <button class="btn ghost sm" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条</span>
      <button class="btn ghost sm" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>

    <!-- 详情抽屉 -->
    <div v-if="detail" class="mask" @click.self="detail = null">
      <div class="drawer">
        <div class="drawer-head">
          <h3>日志详情 #{{ detail.id }}</h3>
          <button class="x" @click="detail = null">×</button>
        </div>
        <div class="drawer-body">
          <p class="k">用户</p><p class="v">{{ detail.username || ('#' + (detail.userId ?? '-')) }}</p>
          <p class="k">问题</p><p class="v">{{ detail.question }}</p>
          <p class="k">回答</p><p class="v pre">{{ detail.answer || '-' }}</p>
          <p class="k">评分</p><p class="v">{{ detail.feedback ?? '未评分' }}{{ detail.feedbackNote ? '（' + detail.feedbackNote + '）' : '' }}</p>
          <p class="k">检索模式</p><p class="v">{{ detail.retrievalMode || '-' }}</p>
          <p class="k">耗时</p><p class="v">{{ detail.latencyMs ?? '-' }}ms</p>
          <template v-if="refs.length">
            <p class="k">依据来源</p>
            <div class="v">
              <div v-for="(r, i) in refs" :key="i" class="ref">
                <strong>{{ r.title || r.docTitle || ('片段 ' + (r.chunkIndex ?? i)) }}</strong>
                <p>{{ r.content || r.snippet || '' }}</p>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { listChatLogs, getChatLogStats, getChatLogDetail } from '../../services/api.js'

const logs = ref([])
const stats = reactive({})
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(15)
const total = ref(0)
const detail = ref(null)
const filter = reactive({ feedback: '', crisis: '', keyword: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const refs = computed(() => {
  const raw = detail.value?.referencesJson || detail.value?.references
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch { return [] }
})

function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 19) : '-' }
function fmtNum(v) { return v == null ? '-' : Math.round(Number(v)) }
function fmtPct(v) {
  if (v == null) return '-'
  const n = Number(v)
  return (n <= 1 ? n * 100 : n).toFixed(0) + '%'
}
function truncate(s, n = 40) {
  if (!s) return '-'
  return s.length > n ? s.slice(0, n) + '...' : s
}

async function loadStats() {
  try {
    const s = await getChatLogStats()
    const data = s?.data ?? s ?? {}
    Object.assign(stats, data)
  } catch (e) {
    error.value = '统计获取失败：' + (e?.message || e)
  }
}

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
      const res = await listChatLogs({
        page: page.value,
        size: size.value,
        feedback: filter.feedback === '' ? undefined : filter.feedback,
        crisis: filter.crisis || undefined,
        keyword: filter.keyword || undefined
      })
      if (Array.isArray(res)) {
        logs.value = res
        total.value = res.length
      } else {
        logs.value = res?.rows ?? res?.data ?? []
        total.value = res?.total ?? res?.totalElements ?? logs.value.length
      }
  } catch (e) {
    error.value = '日志获取失败：' + (e?.message || e)
    logs.value = []
  } finally {
    loading.value = false
  }
}

function loadAll() { loadStats(); loadLogs() }
function reload() { page.value = 1; loadLogs() }
function goPage(p) { if (p < 1 || p > totalPages.value) return; page.value = p; loadLogs() }

async function openDetail(l) {
  detail.value = l
  try {
    const d = await getChatLogDetail(l.id)
    if (d) detail.value = d?.data ?? d
  } catch (e) {
    /* 保留列表行数据 */
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page { max-width: 1080px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; }
.eyebrow { color: #7a8794; font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin: 0 0 6px; }
.page-head h2 { color: #2d3741; font-size: 24px; margin: 0; letter-spacing: -.01em; }
.sub { color: #8a96a3; font-size: 13px; margin: 5px 0 0; }

.alert { padding: 10px 14px; border-radius: 9px; font-size: 13px; margin: 0 0 14px; background: #fdeced; border: 1px solid #f4c4c8; color: #c0392b; }

.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.card { padding: 16px 18px; border-radius: 12px; display: flex; flex-direction: column; gap: 4px; background: #ffffff; border: 1px solid #e4e8ee; }
.card.danger { border-color: #f0c3c5; }
.c-val { color: #2d3741; font-size: 26px; font-weight: 800; }
.card.danger .c-val { color: #c0392b; }
.c-label { color: #8a96a3; font-size: 12px; }

.filters { display: flex; gap: 10px; padding: 12px; border-radius: 12px; margin-bottom: 16px; background: #ffffff; border: 1px solid #e4e8ee; flex-wrap: wrap; }
.filters select, .filters input { padding: 9px 12px; border-radius: 9px; border: 1px solid #d8dee6; background: #fbfcfd; color: #2d3741; font-size: 13px; outline: none; }
.filters input:focus, .filters select:focus { border-color: #2f6df0; }
.filters input { flex: 1; min-width: 160px; }

.table { border-radius: 14px; overflow: hidden; background: #ffffff; border: 1px solid #e4e8ee; }
.row { display: grid; grid-template-columns: 1.3fr 1fr 1.8fr 60px 80px 70px 80px; gap: 10px; align-items: center; padding: 11px 16px; border-bottom: 1px solid #edf0f4; font-size: 13px; color: #3a4552; }
.row.head { color: #5b6775; font-weight: 700; background: #f5f7fa; }
.row:last-child { border-bottom: 0; }
.row.empty { color: #8a96a3; justify-content: center; padding: 26px; }
.q { color: #2d3741; }
.mono { font-family: ui-monospace, Menlo, monospace; } .small { font-size: 12px; color: #6b7785; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 99px; font-size: 11px; font-weight: 700; }
.badge.bad { background: #fdeced; color: #c0392b; }

.pager { display: flex; align-items: center; gap: 14px; justify-content: center; margin-top: 16px; color: #6b7785; font-size: 13px; }

.btn { border: 0; border-radius: 9px; padding: 10px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .16s; }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn.primary { background: #2f6df0; color: #fff; }
.btn.ghost { background: #ffffff; color: #5b6775; border: 1px solid #d8dee6; }
.btn.ghost:hover:not(:disabled) { background: #f0f4f8; }
.btn.sm { padding: 6px 11px; font-size: 12px; }

.mask { position: fixed; inset: 0; background: rgba(30,40,52,.4); display: flex; justify-content: flex-end; z-index: 50; }
.drawer { width: min(480px, 92vw); height: 100%; background: #ffffff; border-left: 1px solid #e4e8ee; display: flex; flex-direction: column; }
.drawer-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #edf0f4; }
.drawer-head h3 { margin: 0; color: #2d3741; font-size: 16px; }
.x { background: transparent; border: 0; color: #8a96a3; font-size: 24px; line-height: 1; cursor: pointer; }
.drawer-body { padding: 18px 20px; overflow-y: auto; }
.k { color: #7a8794; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .1em; margin: 14px 0 4px; }
.k:first-child { margin-top: 0; }
.v { color: #2d3741; font-size: 14px; margin: 0; word-break: break-word; }
.v.pre { white-space: pre-wrap; line-height: 1.7; }
.ref { background: #f7f9fb; border-left: 3px solid #2f6df0; border-radius: 8px; padding: 9px 12px; margin-top: 8px; }
.ref strong { color: #2d3741; font-size: 13px; }
.ref p { color: #6b7785; font-size: 12px; margin: 4px 0 0; line-height: 1.5; }

@media (max-width: 820px) {
  .cards { grid-template-columns: repeat(2, 1fr); }
  .row { grid-template-columns: 1fr 1fr; gap: 6px 12px; }
  .row.head { display: none; }
}
</style>
