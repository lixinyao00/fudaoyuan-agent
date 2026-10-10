const BASE = '/api'
const TOKEN_KEY = 'rag_auth_token'

/* ---------------- token 管理 ---------------- */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

function authHeaders(extra = {}) {
  const headers = { ...extra }
  const token = getToken()
  if (token) headers['Authorization'] = 'Bearer ' + token
  return headers
}

function redirectToLogin() {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

/* ---------------- 通用请求 ---------------- */
async function request(url, options = {}) {
  const resp = await fetch(BASE + url, {
    ...options,
    headers: authHeaders({ 'Content-Type': 'application/json', ...options.headers })
  })

  if (resp.status === 401) {
    clearToken()
    redirectToLogin()
    throw new Error('登录已过期，请重新登录')
  }

  if (!resp.ok) {
    const err = await resp.json().catch(() => ({ message: resp.statusText }))
    throw new Error(err.message || ('请求失败(' + resp.status + ')'))
  }

  if (resp.status === 204) return null
  // 兼容后端可能返回纯文本 / 空体
  const text = await resp.text()
  return text ? JSON.parse(text) : null
}

/**
 * 上传（multipart/form-data）
 * @param {string} url 相对路径
 * @param {File} file 文件
 * @param {string} field 字段名
 */
export async function upload(url, file, field = 'file') {
  const form = new FormData()
  form.append(field, file)
  const resp = await fetch(BASE + url, {
    method: 'POST',
    headers: authHeaders(),
    body: form
  })
  if (resp.status === 401) {
    clearToken()
    redirectToLogin()
    throw new Error('登录已过期，请重新登录')
  }
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({ message: resp.statusText }))
    throw new Error(err.message || ('上传失败(' + resp.status + ')'))
  }
  const text = await resp.text()
  return text ? JSON.parse(text) : null
}

/* ---------------- 鉴权 ---------------- */
export function authLogin(username, password) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

export function authMe() {
  return request('/auth/me')
}

/* ---------------- 问答 ---------------- */
export function chat(question) {
  return request('/chat', {
    method: 'POST',
    body: JSON.stringify({ question })
  })
}

export function health() {
  return request('/health')
}

/**
 * 流式问答 (SSE)
 * @param {string} question 问题
 * @param {object} callbacks - { onReferences, onChunk, onDone, onError }
 * @returns {Function} abort function
 */
export function chatStream(question, callbacks) {
  const controller = new AbortController()

  fetch(BASE + '/chat/stream', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ question }),
    signal: controller.signal
  }).then(response => {
    if (response.status === 401) {
      clearToken()
      redirectToLogin()
      throw new Error('登录已过期，请重新登录')
    }
    if (!response.ok) {
      throw new Error('请求失败: ' + response.status)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let doneCalled = false

    function processLine(line) {
      // 跳过空行和事件类型行
      if (!line || line.startsWith('event:')) return

      // 处理data行
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data || data === '[DONE]') return

        try {
          const parsed = JSON.parse(data)
          const eventType = parsed.type

          if (eventType === 'references' && callbacks.onReferences) {
            callbacks.onReferences(parsed.data)
          } else if (eventType === 'chunk' && callbacks.onChunk) {
            // 过滤null字符
            const content = (parsed.data || '').replace(/\0/g, '')
            if (content) callbacks.onChunk(content)
          } else if (eventType === 'done' && callbacks.onDone && !doneCalled) {
            doneCalled = true
            callbacks.onDone(parsed.data)
            return
          } else if (eventType === 'error' && callbacks.onError) {
            callbacks.onError(parsed.data)
            return
          }
        } catch (e) {
          // 忽略解析错误
        }
      }
    }

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          if (!doneCalled && callbacks.onDone) {
            doneCalled = true
            callbacks.onDone()
          }
          return
        }

        // 解码并过滤null字符
        const decoded = decoder.decode(value, { stream: true }).replace(/\0/g, '')
        buffer += decoded

        // 按行分割处理
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          processLine(line)
        }

        if (!doneCalled) read()
      }).catch(err => {
        if (err.name !== 'AbortError' && callbacks.onError && !doneCalled) {
          doneCalled = true
          callbacks.onError(err.message)
        }
      })
    }

    read()
  }).catch(err => {
    if (err.name !== 'AbortError' && callbacks.onError) {
      callbacks.onError(err.message)
    }
  })

  return () => controller.abort()
}

/* ---------------- 后台：知识库 ---------------- */
export function listAdminDocuments() {
  return request('/admin/documents')
}
export function uploadAdminDocument(file) {
  return upload('/admin/documents/upload', file)
}
export function deleteAdminDocument(id) {
  return request('/admin/documents/' + id, { method: 'DELETE' })
}

/* ---------------- 后台：问答监控 ---------------- */
export function listChatLogs(params = {}) {
  const qs = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  ).toString()
  return request('/admin/chat-logs' + (qs ? '?' + qs : ''))
}
export function getChatLogStats() {
  return request('/admin/chat-logs/stats')
}
export function getChatLogDetail(id) {
  return request('/admin/chat-logs/' + id)
}

/* ---------------- 后台：系统配置 ---------------- */
export function listConfigs() {
  return request('/admin/config')
}
export function updateConfig(key, value) {
  return request('/admin/config/' + encodeURIComponent(key), {
    method: 'PUT',
    body: JSON.stringify({ value })
  })
}

/* ---------------- 后台：危机告警 ---------------- */
export function listCrisisAlerts(params = {}) {
  const qs = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  ).toString()
  return request('/admin/crisis-alerts' + (qs ? '?' + qs : ''))
}
export function handleCrisisAlert(id) {
  return request('/admin/crisis-alerts/' + id + '/handle', { method: 'POST' })
}
