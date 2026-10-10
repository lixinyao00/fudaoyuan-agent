<template>
  <div class="chat-page">
    <header class="header">
      <div class="header-left">
        <h1>辅导员AI问答助手</h1>
      </div>
      <div class="header-right" v-if="auth.isLoggedIn.value">
        <span class="user-chip">{{ auth.displayName.value }}</span>
        <span class="role-chip" :class="auth.isAdmin.value ? 'admin' : 'student'">{{ auth.isAdmin.value ? '管理员' : '学生' }}</span>
        <RouterLink v-if="auth.isAdmin.value" to="/admin" class="ghost-link">后台管理</RouterLink>
        <span class="status-dot" :class="connected ? 'online' : 'offline'"></span>
        <span class="status-text">{{ connected ? '在线' : '连接中...' }}</span>
        <button class="logout-btn" type="button" @click="logout">退出</button>
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
          <!-- 助手消息内容为空且仍在等待首个流式片段时，就地显示打字动画（避免独立 loading 块造成双头像） -->
          <div v-if="!msg.content && msg.role === 'assistant' && loading && i === messages.length - 1" class="bubble loading-bubble">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
          <div v-else-if="msg.content" class="bubble" v-html="formatMessage(msg.content)"></div>

          <!-- Citations -->
          <div v-if="msg.references && msg.references.length > 0" class="citations">
            <details>
              <summary>📖 依据来源 ({{ msg.references.length }}条)</summary>
              <div v-for="(c, j) in msg.references" :key="j" class="citation-item">
                <div class="citation-title">文档片段 {{ c.chunkIndex }}</div>
                <div class="citation-snippet">{{ c.content }}</div>
              </div>
            </details>
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
import { useRouter } from 'vue-router'
import { chatStream, health } from '../services/api.js'
import auth from '../stores/auth.js'

const router = useRouter()

function logout() {
  auth.logout()
  router.replace('/login')
}

const messages = ref([])
const input = ref('')
const loading = ref(false)
const connected = ref(false)
const chatContainer = ref(null)
const inputEl = ref(null)
const streamingContent = ref('')
let abortFn = null

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
  streamingContent.value = ''

  // 添加一个空的助手消息用于流式更新
  const assistantIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    references: []
  })

  try {
    abortFn = chatStream(msg, {
      onReferences(refs) {
        messages.value[assistantIndex].references = refs
      },
      onChunk(chunk) {
        streamingContent.value += chunk
        messages.value[assistantIndex].content = streamingContent.value
        scrollToBottom()
      },
      onDone(fullAnswer) {
        if (fullAnswer) {
          messages.value[assistantIndex].content = fullAnswer
        }
        loading.value = false
        streamingContent.value = ''
        abortFn = null
        scrollToBottom()
      },
      onError(err) {
        messages.value[assistantIndex].content = '抱歉，网络错误，请稍后重试。' + (err || '')
        loading.value = false
        streamingContent.value = ''
        abortFn = null
        scrollToBottom()
      }
    })
  } catch (e) {
    messages.value[assistantIndex].content = '抱歉，网络错误，请稍后重试。' + e.message
    loading.value = false
    streamingContent.value = ''
    scrollToBottom()
  }
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
.user-chip { color:#4a5766; font-weight:700; }
.role-chip { padding:2px 9px; border-radius:99px; font-size:11px; font-weight:700; }
.role-chip.student { background:#eaf1fe; color:#1f64da; }
.role-chip.admin { background:#f0eefe; color:#6b4fd0; }
.ghost-link { color:#1f64da; font-weight:700; text-decoration:none; padding:5px 10px; border:1px solid #cddcf8; border-radius:9px; }
.ghost-link:hover { background:#eaf1fe; }
.logout-btn { background:#ffffff; color:#5b6775; border:1px solid #d8dee6; border-radius:9px; padding:6px 12px; font-size:12px; font-weight:700; cursor:pointer; }
.logout-btn:hover { background:#fdeced; border-color:#e2b3b7; color:#c0392b; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.online { background: #4caf50; }
.status-dot.offline { background: #ff9800; }

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

.citations { margin-top: 8px; }
.citations details { background: #f8f9fa; border-radius: 8px; padding: 8px 12px; }
.citations summary { cursor: pointer; color: #1a73e8; font-weight: 500; font-size: 13px; }
.citation-item {
  margin-top: 8px; padding: 8px 10px; background: white;
  border-radius: 6px; border-left: 3px solid #1a73e8;
}
.citation-title { font-weight: 600; font-size: 13px; color: #333; }
.citation-snippet { font-size: 12px; color: #555; margin-top: 4px; line-height: 1.5; }

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
.chat-page { min-height:100vh; height:100vh; color:#2d3741; background:#f5f7fa; }
.header { padding:18px clamp(18px,5vw,64px); background:#fff; border-bottom:1px solid #e4e8ee; box-shadow:0 2px 10px rgba(40,56,74,.06); }
.header h1 { color:#2d3741; font-size:18px; letter-spacing:-.01em; }.subtitle { color:#7a8794; opacity:1; }.status-text { color:#6b7785; }.status-dot.online { background:#5d9a78; }
.chat-container { max-width:920px; padding:40px 24px 28px; }.welcome { padding:clamp(48px,10vh,100px) 20px 45px; }.welcome-icon { display:grid; place-items:center; width:68px; height:68px; margin:0 auto 20px; border-radius:22px; color:#1f64da; background:#eaf1fe; font-size:30px; }.welcome h2 { color:#2d3741; font-size:30px; letter-spacing:-.02em; }.welcome p { color:#8a96a3; }.quick-questions { gap:10px; }.quick-btn { color:#5b6775; background:#fff; border-color:#d8dee6; padding:10px 15px; border-radius:99px; }.quick-btn:hover { color:#1f64da; border-color:#1f64da; background:#eaf1fe; transform:translateY(-2px); }
.message { gap:12px; margin-bottom:20px; }.avatar { width:36px;height:36px; border:1px solid #e4e8ee; }.message.user .avatar { background:#eaf1fe; color:#1f64da; }.message.assistant .avatar { background:#ecf7f0; color:#1f7a45; }.bubble-wrapper { max-width:78%; }.bubble { padding:14px 17px; border-radius:18px; font-size:14px; line-height:1.8; }.message.user .bubble { background:#2f6df0; color:#fff; border-bottom-right-radius:5px; }.message.assistant .bubble { color:#2d3741; background:#fff; border-color:#e4e8ee; border-bottom-left-radius:5px; box-shadow:0 1px 3px rgba(40,56,74,.08); }.citations details { color:#5b6775; background:#f7f9fb; border:1px solid #e4e8ee; }.citations summary { color:#1f64da; }.citation-item { background:#fff; border-left-color:#2f6df0; }.citation-title { color:#2d3741; }.citation-snippet { color:#6b7785; }
.input-area { padding:18px 24px 24px; background:#f5f7fa; border:0; }.input-wrapper { max-width:920px; padding:7px; background:#fff; border:1px solid #d8dee6; border-radius:15px; box-shadow:0 8px 22px rgba(40,56,74,.08); }.input-wrapper textarea { color:#2d3741; background:transparent; border:0; padding:11px 13px; }.input-wrapper textarea::placeholder { color:#9aa9b8; }.textarea:focus, textarea:focus { border:0; box-shadow:none; }.send-btn { min-width:96px; padding:11px 18px; border-radius:10px; background:#2f6df0; color:#fff; font-weight:700; }.send-btn:hover { background:#2360db; }.send-btn:disabled { background:#c3cbd6; color:#fff; }
@media(max-width:620px){.header{padding:15px 18px}.subtitle{display:none}.header-right{gap:7px}.chat-container{padding:24px 14px}.bubble-wrapper{max-width:84%}.input-area{padding:12px}.send-btn{min-width:72px;padding-inline:12px}}
</style>
