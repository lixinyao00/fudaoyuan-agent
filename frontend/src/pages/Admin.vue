<template>
  <div class="admin-page">
    <header class="header">
      <h1>管理后台</h1>
      <div class="header-right">
        <router-link to="/chat" class="link">问答页面</router-link>
        <button @click="logout" class="logout-btn">退出</button>
      </div>
    </header>

    <div class="container">
      <!-- Tabs -->
      <div class="tabs">
        <button v-for="t in tabs" :key="t.key" :class="{ active: tab === t.key }" @click="tab = t.key">
          {{ t.label }}
          <span v-if="t.count !== undefined" class="badge">{{ t.count }}</span>
        </button>
      </div>

      <!-- Knowledge Chunks -->
      <div v-if="tab === 'chunks'" class="panel">
        <div class="panel-header">
          <h3>知识条目 ({{ chunks.length }})</h3>
          <div class="filters">
            <select v-model="chunkFilter" @change="loadChunks">
              <option value="">全部</option>
              <option value="PENDING">待审核</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已拒绝</option>
            </select>
            <button @click="approveAll" class="approve-all-btn" v-if="chunks.some(c => c.review_status === 'PENDING')">
              全部通过
            </button>
          </div>
        </div>
        <table>
          <thead><tr><th>ID</th><th>章节</th><th>页码</th><th>板块</th><th>内容预览</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="c in chunks" :key="c.id">
              <td>{{ c.id }}</td>
              <td>{{ c.chapter_title }}</td>
              <td>{{ c.page_start }}-{{ c.page_end }}</td>
              <td><span class="cat-tag" v-for="cat in (c.categories||'').split(',')" :key="cat">{{ cat }}</span></td>
              <td class="content-cell">{{ (c.content || '').slice(0, 60) }}...</td>
              <td><span :class="'status-' + c.review_status">{{ c.review_status }}</span></td>
              <td>
                <button v-if="c.review_status === 'PENDING'" @click="approve(c)" class="btn-approve">通过</button>
                <button v-if="c.review_status === 'PENDING'" @click="reject(c)" class="btn-reject">拒绝</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Feedback -->
      <div v-if="tab === 'feedback'" class="panel">
        <h3>用户反馈 ({{ feedbacks.length }})</h3>
        <table>
          <thead><tr><th>消息ID</th><th>类型</th><th>原因</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="f in feedbacks" :key="f.id">
              <td>{{ (f.message_id||'').slice(0,12) }}...</td>
              <td><span :class="'fb-' + f.feedback_type">{{ f.feedback_type }}</span></td>
              <td>{{ f.reason || '-' }}</td>
              <td>{{ formatTime(f.created_at) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="feedbacks.length === 0" class="empty">暂无反馈</p>
      </div>

      <!-- Risks -->
      <div v-if="tab === 'risks'" class="panel">
        <h3>风险事件 ({{ risks.length }})</h3>
        <table>
          <thead><tr><th>会话</th><th>风险等级</th><th>类型</th><th>描述</th><th>已解决</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="r in risks" :key="r.id">
              <td>{{ (r.conversation_id||'').slice(0,8) }}...</td>
              <td><span :class="'risk-' + r.risk_level">{{ r.risk_level }}</span></td>
              <td>{{ r.risk_type }}</td>
              <td>{{ r.description }}</td>
              <td>{{ r.resolved ? '✓' : '✗' }}</td>
              <td>{{ formatTime(r.created_at) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="risks.length === 0" class="empty">暂无风险事件</p>
      </div>

      <!-- Audit -->
      <div v-if="tab === 'audit'" class="panel">
        <h3>审计日志</h3>
        <table>
          <thead><tr><th>操作</th><th>操作人</th><th>目标</th><th>详情</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="a in audits" :key="a.id">
              <td>{{ a.action }}</td>
              <td>{{ a.operator }}</td>
              <td>{{ a.target_type }}:{{ a.target_id }}</td>
              <td>{{ (a.details||'').slice(0,50) }}</td>
              <td>{{ formatTime(a.created_at) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="audits.length === 0" class="empty">暂无日志</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getChunks, reviewChunk, getFeedbacks, getRisks, getAudit, adminLogout } from '../services/api.js'

const router = useRouter()
const token = ref(localStorage.getItem('admin_token') || '')
const tab = ref('chunks')
const chunkFilter = ref('')
const chunks = ref([])
const feedbacks = ref([])
const risks = ref([])
const audits = ref([])

const tabs = computed(() => [
  { key: 'chunks', label: '知识条目', count: chunks.value.length },
  { key: 'feedback', label: '用户反馈', count: feedbacks.value.length },
  { key: 'risks', label: '风险事件', count: risks.value.length },
  { key: 'audit', label: '审计日志' }
])

onMounted(() => {
  if (!token.value) { router.push('/login'); return }
  loadAll()
})

async function loadAll() {
  await Promise.all([loadChunks(), loadFeedbacks(), loadRisks(), loadAudits()])
}

async function loadChunks() {
  try { chunks.value = await getChunks(token.value, chunkFilter.value || undefined) } catch {}
}
async function loadFeedbacks() {
  try { feedbacks.value = await getFeedbacks(token.value) } catch {}
}
async function loadRisks() {
  try { risks.value = await getRisks(token.value) } catch {}
}
async function loadAudits() {
  try { audits.value = await getAudit(token.value) } catch {}
}

async function approve(chunk) {
  await reviewChunk(token.value, chunk.id, 'APPROVED')
  chunk.review_status = 'APPROVED'
}
async function reject(chunk) {
  await reviewChunk(token.value, chunk.id, 'REJECTED')
  chunk.review_status = 'REJECTED'
}
async function approveAll() {
  for (const c of chunks.value) {
    if (c.review_status === 'PENDING') {
      await reviewChunk(token.value, c.id, 'APPROVED')
      c.review_status = 'APPROVED'
    }
  }
}

async function logout() {
  try { await adminLogout() } catch {}
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_user')
  router.push('/login')
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6fa; }
.header {
  background: #1a73e8; color: white; padding: 14px 24px;
  display: flex; justify-content: space-between; align-items: center;
}
.header h1 { font-size: 18px; }
.header-right { display: flex; gap: 12px; align-items: center; }
.link { color: white; font-size: 13px; opacity: 0.85; }
.link:hover { opacity: 1; }
.logout-btn {
  background: rgba(255,255,255,0.15); border: 1px solid rgba(255,255,255,0.3);
  color: white; padding: 4px 12px; border-radius: 6px; font-size: 12px;
}

.container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }

.tabs { display: flex; gap: 4px; margin-bottom: 16px; }
.tabs button {
  padding: 10px 20px; background: white; border: none;
  border-radius: 8px 8px 0 0; font-size: 14px; color: #666;
  display: flex; align-items: center; gap: 6px;
}
.tabs button.active { background: #1a73e8; color: white; }
.badge {
  background: #e8e8e8; padding: 1px 6px; border-radius: 10px;
  font-size: 11px; color: #666;
}
.tabs button.active .badge { background: rgba(255,255,255,0.2); color: white; }

.panel {
  background: white; border-radius: 0 8px 8px 8px;
  padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.panel-header h3 { font-size: 16px; }
.filters { display: flex; gap: 8px; }
.filters select { padding: 6px 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 13px; }
.approve-all-btn {
  padding: 6px 14px; background: #e8f5e9; color: #2e7d32;
  border: none; border-radius: 6px; font-size: 13px;
}

table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f0f0f0; }
th { background: #f8f9fa; font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; }
.content-cell { max-width: 250px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.cat-tag {
  display: inline-block; padding: 1px 6px; margin: 1px;
  background: #e8f0fe; color: #1a73e8; border-radius: 4px; font-size: 11px;
}
.status-PENDING { color: #e65100; font-weight: 600; }
.status-APPROVED { color: #2e7d32; font-weight: 600; }
.status-REJECTED { color: #c62828; font-weight: 600; }

.btn-approve { background: #e8f5e9; color: #2e7d32; border: none; padding: 4px 10px; border-radius: 4px; font-size: 12px; margin-right: 4px; }
.btn-reject { background: #fce4ec; color: #c62828; border: none; padding: 4px 10px; border-radius: 4px; font-size: 12px; }

.fb-HELPFUL { color: #2e7d32; }
.fb-NOT_HELPFUL { color: #e65100; }
.fb-REPORT { color: #c62828; }

.risk-CRITICAL { color: #c62828; font-weight: 600; }
.risk-HIGH { color: #e65100; font-weight: 600; }
.risk-MEDIUM { color: #e65100; }

.empty { color: #999; text-align: center; padding: 30px; font-size: 14px; }
h3 { font-size: 16px; margin-bottom: 12px; color: #333; }
/* refreshed admin workspace */
.admin-page{color:#26364a;background:#f4f6f8}.admin-page .header{background:#fff;border-bottom:1px solid #e1e7ed;padding:18px clamp(18px,5vw,64px)}.admin-page .header h1{color:#26364a}.link{color:#58799b}.logout-btn{border-color:#c8d5e1;color:#58799b;border-radius:99px;padding:7px 14px}.container{max-width:1240px;margin:30px auto;padding:0 20px}.tabs{gap:8px;overflow:auto}.tabs button{color:#6f8092;background:#fff;border:1px solid #e1e7ed;border-radius:10px;padding:11px 16px;white-space:nowrap}.tabs button.active{color:#fff;background:#58799b;border-color:#58799b}.tabs button.active .badge{color:#58799b;background:#fff}.panel{color:#53677b;background:#fff;border:1px solid #e1e7ed;border-radius:16px;padding:22px;box-shadow:0 8px 24px #253b5310;overflow:auto}.panel h3,.panel-header h3{color:#26364a}.filters select{color:#53677b;background:#f8fafc;border-color:#dce4ec}.approve-all-btn{color:#58799b;background:#eef3f7}.panel table{min-width:720px}.panel th{color:#708296;background:#f7f9fb}.panel td{border-color:#edf1f4}.content-cell{color:#61758a}.cat-tag{color:#58799b;background:#eef3f7}.empty{color:#8a98a7}.btn-approve{color:#527e64;background:#edf6f0}.btn-reject{color:#a04f59;background:#fff1f2}
</style>
