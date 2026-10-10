<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">后台 / 知识库</p>
        <h2>知识库管理</h2>
        <p class="sub">上传文档、查看分块数、删除过期资料</p>
      </div>
      <div class="actions">
        <input ref="fileInput" type="file" hidden @change="onFileChange" />
        <button class="btn primary" :disabled="uploading" @click="pickFile">
          {{ uploading ? '上传中...' : '＋ 上传文档' }}
        </button>
        <button class="btn ghost" :disabled="loading" @click="loadList">刷新</button>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="success" class="alert success">{{ success }}</p>

    <div class="table glass">
      <div class="row head">
        <span>ID</span><span>标题</span><span>文件名</span><span>分块数</span><span>状态</span><span>创建时间</span><span>操作</span>
      </div>
      <div v-if="loading" class="row empty">加载中...</div>
      <div v-else-if="docs.length === 0" class="row empty">暂无文档</div>
      <div v-for="d in docs" :key="d.id" class="row">
        <span>{{ d.id }}</span>
        <span class="title">{{ d.title || '-' }}</span>
        <span class="mono">{{ d.fileName || '-' }}</span>
        <span>{{ d.chunkCount ?? '-' }}</span>
        <span><span class="badge" :class="statusClass(d.status)">{{ d.status || '-' }}</span></span>
        <span class="mono small">{{ fmt(d.createdAt) }}</span>
        <span>
          <button class="btn danger sm" :disabled="deleting === d.id" @click="remove(d)">
            {{ deleting === d.id ? '删除中' : '删除' }}
          </button>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listAdminDocuments, uploadAdminDocument, deleteAdminDocument } from '../../services/api.js'

const docs = ref([])
const loading = ref(false)
const uploading = ref(false)
const deleting = ref(null)
const error = ref('')
const success = ref('')
const fileInput = ref(null)

function fmt(v) {
  if (!v) return '-'
  const s = String(v).replace('T', ' ').slice(0, 19)
  return s
}
function statusClass(status) {
  if (status === 'READY' || status === 'PROCESSED') return 'ok'
  if (status === 'FAILED' || status === 'ERROR') return 'bad'
  return 'warn'
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAdminDocuments()
    docs.value = Array.isArray(res) ? res : (res?.data ?? res?.content ?? [])
  } catch (e) {
    error.value = '获取文档列表失败：' + (e?.message || e)
    docs.value = []
  } finally {
    loading.value = false
  }
}

function pickFile() {
  success.value = ''
  fileInput.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  success.value = ''
  try {
    await uploadAdminDocument(file)
    success.value = '上传成功：' + file.name
    await loadList()
  } catch (err) {
    error.value = '上传失败：' + (err?.message || err)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function remove(d) {
  if (!confirm('确认删除文档「' + (d.title || d.fileName || d.id) + '」？该操作会同时清理向量库。')) return
  deleting.value = d.id
  error.value = ''
  success.value = ''
  try {
    await deleteAdminDocument(d.id)
    success.value = '已删除文档 ' + d.id
    await loadList()
  } catch (err) {
    error.value = '删除失败：' + (err?.message || err)
  } finally {
    deleting.value = null
  }
}

onMounted(loadList)
</script>

<style scoped>
.page { max-width: 1040px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; }
.eyebrow { color: #7a8794; font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin: 0 0 6px; }
.page-head h2 { color: #2d3741; font-size: 24px; margin: 0; letter-spacing: -.01em; }
.sub { color: #8a96a3; font-size: 13px; margin: 5px 0 0; }
.actions { display: flex; gap: 10px; }

.btn { border: 0; border-radius: 9px; padding: 10px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .16s, transform .16s; }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn.primary { background: #2f6df0; color: #fff; }
.btn.primary:hover:not(:disabled) { background: #2360db; }
.btn.ghost { background: #ffffff; color: #5b6775; border: 1px solid #d8dee6; }
.btn.ghost:hover:not(:disabled) { background: #f0f4f8; }
.btn.danger { background: #ffffff; color: #c0392b; border: 1px solid #e2b3b7; }
.btn.danger:hover:not(:disabled) { background: #fdeced; }
.btn.sm { padding: 6px 11px; font-size: 12px; }

.alert { padding: 10px 14px; border-radius: 9px; font-size: 13px; margin: 0 0 14px; }
.alert.error { background: #fdeced; border: 1px solid #f4c4c8; color: #c0392b; }
.alert.success { background: #ecf7f0; border: 1px solid #c3e3cf; color: #1f7a45; }

.table { border-radius: 14px; overflow: hidden; }
.row { display: grid; grid-template-columns: 56px 1.4fr 1.4fr 80px 90px 1.4fr 90px; gap: 10px; align-items: center; padding: 12px 16px; border-bottom: 1px solid #edf0f4; font-size: 13px; }
.row.head { color: #5b6775; font-weight: 700; background: #f5f7fa; }
.row:last-child { border-bottom: 0; }
.row.empty { color: #8a96a3; justify-content: center; padding: 26px; }
.title { color: #2d3741; font-weight: 600; }
.mono { color: #6b7785; font-family: ui-monospace, Menlo, monospace; }
.small { font-size: 12px; }
.badge { display: inline-block; padding: 3px 9px; border-radius: 99px; font-size: 11px; font-weight: 600; }
.badge.ok { background: #e6f4ec; color: #1f7a45; }
.badge.warn { background: #fbf3df; color: #8a6d1a; }
.badge.bad { background: #fdeced; color: #c0392b; }

@media (max-width: 820px) {
  .row { grid-template-columns: 1fr 1fr; gap: 6px 12px; }
  .row.head { display: none; }
}
</style>
