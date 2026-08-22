<template>
  <div class="chat-page">
    <header class="header">
      <div class="header-left">
        <h1>辅导员AI问答助手</h1>
      </div>
      <div class="header-right">
        <span class="status-dot" :class="connected ? 'online' : 'offline'"></span>
        <span class="status-text">{{ connected ? '在线' : '连接中...' }}</span>
        <router-link to="/admin" class="admin-link">管理后台</router-link>
      </div>
    </header>
    <div class="chat-container" ref="chatContainer">
      <!-- Welcome -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">🎓</div>
        <h2>欢迎使用辅导员AI问答助手</h2>
        <p>我可以帮您解答关于学校政策和日常事务的问题</p>
        <div class="quick-questions">
          <button v-for="q in quickQuestions" :key="q" @click="sendMessage(q)" class="quick-btn">{{ q }}</button>
        </div>
      </div>

      <!-- Messages -->
      <div v-for="(msg, i) in messages" :key="i" class="message" :class="msg.role">
        <div class="avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="bubble-wrapper">
          <div class="bubble" v-html="formatMessage(msg.content)"></div>

          <!-- Risk badge -->
          <div v-if="msg.risk_level && msg.risk_level !== 'NONE'" class="risk-badge" :class="msg.risk_level">
            {{ msg.risk_level === 'CRITICAL' ? '🚨 紧急风险' : '⚠️ ' + msg.risk_level }}
            <span v-if="msg.needs_human_review"> · 建议联系辅导员</span>
          </div>

          <!-- Citations -->
          <div v-if="msg.citations && msg.citations.length > 0" class="citations">
            <details>
              <summary>📖 依据来源 ({{ msg.citations.length }}条)</summary>
              <div v-for="(c, j) in msg.citations" :key="j" class="citation-item">
                <div class="citation-title">{{ c.chapter_title || '相关规定' }}</div>
                <div class="citation-meta">
                  页码: {{ c.page_start }}-{{ c.page_end }}
                  <span v-if="c.article_ref"> · {{ c.article_ref }}</span>
                  · 相关度: {{ (c.score * 100).toFixed(0) }}%
                </div>
                <div class="citation-snippet">{{ c.content_snippet }}</div>
              </div>
            </details>
          </div>

          <!-- Feedback -->
          <div v-if="msg.role === 'assistant' && msg.request_id" class="feedback-bar">
            <button @click="sendFeedback(msg, 'HELPFUL')" :disabled="msg._feedback" class="fb-btn">
              {{ msg._feedback === 'HELPFUL' ? '✓ 已反馈' : '👍 有帮助' }}
            </button>
            <button @click="sendFeedback(msg, 'NOT_HELPFUL')" :disabled="msg._feedback" class="fb-btn">
              {{ msg._feedback === 'NOT_HELPFUL' ? '✓ 已反馈' : '👎 无帮助' }}
            </button>
            <button @click="sendFeedback(msg, 'REPORT')" :disabled="msg._feedback" class="fb-btn report">
              {{ msg._feedback === 'REPORT' ? '✓ 已举报' : '🚩 举报' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="message assistant">
        <div class="avatar">🤖</div>
        <div class="bubble-wrapper">
          <div class="bubble loading-bubble">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Input -->
    <div class="input-area">
      <div class="input-wrapper">
        <textarea
          v-model="input"
          @keydown.enter.exact.prevent="sendMessage()"
          placeholder="请输入您的问题..."
          rows="1"
          ref="inputEl"
        ></textarea>
        <button @click="sendMessage()" :disabled="!input.trim() || loading" class="send-btn">
          {{ loading ? '思考中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { chat, submitFeedback, health } from '../services/api.js'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const connected = ref(false)
const conversationId = ref(null)
const chatContainer = ref(null)
const inputEl = ref(null)

const quickQuestions = [
  '奖学金怎么申请？',
  '请假需要什么手续？',
  '宿舍管理有什么规定？',
  '学位授予条件是什么？',
  '学分不够怎么办？',
  '考试违纪怎么处理？'
]

onMounted(async () => {
  try {
    const h = await health()
    connected.value = h.status === 'UP'
  } catch { connected.value = false }
})

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}

async function sendMessage(text) {
  const msg = (text || input.value).trim()
  if (!msg || loading.value) return

  input.value = ''
  messages.value.push({ role: 'user', content: msg })
  scrollToBottom()

  loading.value = true
  try {
    const resp = await chat(msg, conversationId.value)
    conversationId.value = resp.conversation_id
    messages.value.push({
      role: 'assistant',
      content: resp.answer,
      category: resp.category,
      citations: resp.citations,
      risk_level: resp.risk_level,
      needs_human_review: resp.needs_human_review,
      confidence: resp.confidence,
      request_id: resp.request_id,
      fallback: resp.fallback
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，网络错误，请稍后重试。' + e.message,
      risk_level: 'NONE'
    })
  }
  loading.value = false
  scrollToBottom()
}

async function sendFeedback(msg, type) {
  try {
    await submitFeedback(msg.request_id, type)
    msg._feedback = type
  } catch {}
}

function formatMessage(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; }

.header {
  background: linear-gradient(135deg, #1a73e8, #4285f4);
  color: white; padding: 14px 24px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.header h1 { font-size: 18px; font-weight: 600; }
.subtitle { font-size: 12px; opacity: 0.8; }
.header-right { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.online { background: #4caf50; }
.status-dot.offline { background: #ff9800; }
.admin-link {
  padding: 4px 12px; border: 1px solid rgba(255,255,255,0.4);
  border-radius: 6px; font-size: 12px; color: white;
}
.admin-link:hover { background: rgba(255,255,255,0.15); }

.chat-container { flex: 1; overflow-y: auto; padding: 20px; max-width: 860px; margin: 0 auto; width: 100%; }

.welcome { text-align: center; padding: 60px 20px; }
.welcome-icon { font-size: 48px; margin-bottom: 12px; }
.welcome h2 { color: #333; margin-bottom: 8px; font-size: 22px; }
.welcome p { color: #666; font-size: 14px; margin-bottom: 24px; }
.quick-questions { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.quick-btn {
  background: white; border: 1px solid #e0e0e0; border-radius: 18px;
  padding: 8px 16px; font-size: 13px; color: #555;
  transition: all 0.2s;
}
.quick-btn:hover { border-color: #1a73e8; color: #1a73e8; background: #e8f0fe; }

.message { display: flex; gap: 10px; margin-bottom: 16px; animation: fadeIn 0.3s; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } }
.message.user { flex-direction: row-reverse; }

.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; flex-shrink: 0;
}
.message.user .avatar { background: #e3f2fd; }
.message.assistant .avatar { background: #e8f5e9; }

.bubble-wrapper { max-width: 72%; }
.bubble {
  padding: 12px 16px; border-radius: 16px;
  line-height: 1.7; font-size: 14px;
  white-space: pre-wrap; word-break: break-word;
}
.message.user .bubble { background: #1a73e8; color: white; border-bottom-right-radius: 4px; }
.message.assistant .bubble {
  background: white; color: #333; border: 1px solid #e8e8e8;
  border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.loading-bubble { display: flex; gap: 4px; padding: 16px 20px; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: #999;
  animation: bounce 1.4s infinite ease-in-out;
}
.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%,80%,100% { transform: scale(0); } 40% { transform: scale(1); } }

.risk-badge {
  display: inline-block; margin-top: 6px; padding: 3px 10px;
  border-radius: 10px; font-size: 12px; font-weight: 600;
}
.risk-badge.CRITICAL { background: #fce4ec; color: #c62828; }
.risk-badge.HIGH { background: #fff3e0; color: #e65100; }
.risk-badge.MEDIUM { background: #fff3e0; color: #e65100; }

.citations { margin-top: 8px; }
.citations details { background: #f8f9fa; border-radius: 8px; padding: 8px 12px; }
.citations summary { cursor: pointer; color: #1a73e8; font-weight: 500; font-size: 13px; }
.citation-item {
  margin-top: 8px; padding: 8px 10px; background: white;
  border-radius: 6px; border-left: 3px solid #1a73e8;
}
.citation-title { font-weight: 600; font-size: 13px; color: #333; }
.citation-meta { font-size: 11px; color: #888; margin-top: 2px; }
.citation-snippet { font-size: 12px; color: #555; margin-top: 4px; line-height: 1.5; }

.feedback-bar { display: flex; gap: 6px; margin-top: 6px; }
.fb-btn {
  background: none; border: 1px solid #ddd; border-radius: 12px;
  padding: 2px 10px; font-size: 11px; color: #666;
  transition: all 0.2s;
}
.fb-btn:hover { border-color: #1a73e8; color: #1a73e8; }
.fb-btn:disabled { opacity: 0.5; cursor: default; }
.fb-btn.report:hover { border-color: #c62828; color: #c62828; }

.input-area {
  padding: 14px 24px; background: white;
  border-top: 1px solid #e8e8e8;
}
.input-wrapper {
  max-width: 860px; margin: 0 auto;
  display: flex; gap: 10px; align-items: flex-end;
}
textarea {
  flex: 1; padding: 10px 14px; border: 1px solid #ddd;
  border-radius: 12px; resize: none; font-size: 14px;
  font-family: inherit; outline: none; min-height: 44px; max-height: 120px;
  line-height: 1.5;
}
textarea:focus { border-color: #1a73e8; }
.send-btn {
  padding: 10px 24px; background: #1a73e8; color: white;
  border: none; border-radius: 12px; font-size: 14px; font-weight: 500;
  transition: background 0.2s;
}
.send-btn:hover { background: #1557b0; }
.send-btn:disabled { background: #ccc; cursor: not-allowed; }

/* refreshed workspace skin */
.chat-page { min-height:100vh; height:100vh; color:#26364a; background:#f4f6f8; }
.header { padding:18px clamp(18px,5vw,64px); background:#fff; border-bottom:1px solid #e1e7ed; box-shadow:0 2px 10px #253b5310; }
.header h1 { color:#26364a; font-size:18px; letter-spacing:-.04em; }.subtitle { color:#7b8b9c; opacity:1; }.status-text { color:#6f8092; }.status-dot.online { background:#5d9a78; }.admin-link { color:#58799b; border-color:#c8d5e1; border-radius:99px; padding:7px 14px; }.admin-link:hover { background:#f0f4f8; }
.chat-container { max-width:920px; padding:40px 24px 28px; }.welcome { padding:clamp(48px,10vh,100px) 20px 45px; }.welcome-icon { display:grid; place-items:center; width:68px; height:68px; margin:0 auto 20px; border-radius:22px; color:#07111f; background:linear-gradient(135deg,#5eead4,#60a5fa); font-size:30px; box-shadow:0 20px 50px #22d3ee30; }.welcome h2 { color:#f3f7ff; font-size:30px; letter-spacing:-.05em; }.welcome p { color:#91a8c4; }.quick-questions { gap:10px; }.quick-btn { color:#b9cbe0; background:#ffffff08; border-color:#9db9e026; padding:10px 15px; border-radius:99px; }.quick-btn:hover { color:#5eead4; border-color:#5eead4aa; background:#5eead411; transform:translateY(-2px); }
.message { gap:12px; margin-bottom:20px; }.avatar { width:36px;height:36px; border:1px solid #ffffff18; }.message.user .avatar { background:#193b55; }.message.assistant .avatar { background:#123c3d; }.bubble-wrapper { max-width:78%; }.bubble { padding:14px 17px; border-radius:18px; font-size:14px; line-height:1.8; }.message.user .bubble { background:linear-gradient(135deg,#2563eb,#0ea5e9); border-bottom-right-radius:5px; }.message.assistant .bubble { color:#d9e6f5; background:#102039; border-color:#9db9e020; border-bottom-left-radius:5px; box-shadow:0 10px 28px #0000001c; }.citations details { color:#a9c0d8; background:#0b182b; border:1px solid #9db9e018; }.citations summary { color:#5eead4; }.citation-item { background:#102039; border-left-color:#5eead4; }.citation-title { color:#dce7f7; }.citation-meta,.citation-snippet { color:#8da4bf; }.feedback-bar .fb-btn { color:#8fa6bf; border-color:#9db9e026; }.fb-btn:hover { color:#5eead4; border-color:#5eead4; }
.input-area { padding:18px 24px 24px; background:linear-gradient(transparent,#f4f6f8 18%); border:0; }.input-wrapper { max-width:920px; padding:7px; background:#fff; border:1px solid #dce4ec; border-radius:15px; box-shadow:0 8px 22px #253b5314; }.input-wrapper textarea { color:#26364a; background:transparent; border:0; padding:11px 13px; }.input-wrapper textarea::placeholder { color:#9aa9b8; }.textarea:focus, textarea:focus { border:0; box-shadow:none; }.send-btn { min-width:96px; padding:11px 18px; border-radius:10px; background:#58799b; color:#fff; font-weight:800; }.send-btn:hover { background:#4c6d8e; }.send-btn:disabled { background:#c4d0db; color:#fff; }
@media(max-width:620px){.header{padding:15px 18px}.subtitle{display:none}.header-right{gap:7px}.chat-container{padding:24px 14px}.bubble-wrapper{max-width:84%}.input-area{padding:12px}.send-btn{min-width:72px;padding-inline:12px}}
</style>
