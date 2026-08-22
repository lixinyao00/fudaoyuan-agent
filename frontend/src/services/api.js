const BASE = '/api/v1'

async function request(url, options = {}) {
  const resp = await fetch(BASE + url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options
  })
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({ message: resp.statusText }))
    throw new Error(err.message || '请求失败')
  }
  return resp.json()
}

export function chat(message, conversationId) {
  return request('/chat', {
    method: 'POST',
    body: JSON.stringify({ message, conversation_id: conversationId, channel: 'web' })
  })
}

export function search(query, category, topK = 8) {
  return request('/retrieval/search', {
    method: 'POST',
    body: JSON.stringify({ query, category, top_k: topK })
  })
}

export function submitFeedback(messageId, feedbackType, reason) {
  return request('/feedback', {
    method: 'POST',
    body: JSON.stringify({ message_id: messageId, feedback_type: feedbackType, reason })
  })
}

export function health() {
  return request('/health')
}

// Admin
export function adminLogin(username, password) {
  return request('/admin/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

export function adminLogout() {
  return request('/admin/logout', { method: 'POST' })
}

export function getChunks(token, status, page = 1) {
  const params = new URLSearchParams({ page, size: 50 })
  if (status) params.set('status', status)
  return request(`/admin/chunks?${params}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function reviewChunk(token, id, status, notes) {
  return request(`/admin/chunks/${id}/review`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ status, notes })
  })
}

export function getFeedbacks(token) {
  return request('/admin/feedback', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function getRisks(token) {
  return request('/admin/risks', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function getAudit(token) {
  return request('/admin/audit', {
    headers: { Authorization: `Bearer ${token}` }
  })
}
