import { reactive, computed, readonly } from 'vue'
import { authLogin, getToken, setToken, clearToken } from '../services/api.js'

const USER_KEY = 'rag_auth_user'

function loadUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function saveUser(user) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
    else localStorage.removeItem(USER_KEY)
  } catch {
    /* ignore */
  }
}

// 轻量 reactive 单例（不引入 Pinia）
const state = reactive({
  token: getToken(),
  user: loadUser()
})

function normalizeUser(raw) {
  if (!raw) return null
  return {
    id: raw.id ?? raw.userId ?? null,
    username: raw.username || '',
    role: raw.role || '',
    displayName: raw.displayName || raw.display_name || raw.username || ''
  }
}

export const auth = {
  state: readonly(state),
  token: computed(() => state.token),
  user: computed(() => state.user),
  role: computed(() => state.user?.role || ''),
  isLoggedIn: computed(() => !!state.token),
  isAdmin: computed(() => state.user?.role === 'ADMIN'),
  displayName: computed(() => state.user?.displayName || state.user?.username || '未登录'),

  setSession(token, user) {
    state.token = token || ''
    state.user = normalizeUser(user)
    setToken(token || '')
    saveUser(state.user)
  },

  async login(username, password) {
    const resp = await authLogin(username, password)
    // 兼容 { token, user } / { token, ...user } / { data: {...} } 等返回结构
    const body = resp?.data && (resp.token === undefined) ? resp.data : resp
    const token = body?.token || body?.accessToken || body?.jwt || ''
    const user = body?.user || body?.currentUser || body || null
    if (!token) {
      throw new Error('登录响应缺少 token')
    }
    this.setSession(token, user)
    return this.user.value
  },

  logout() {
    state.token = ''
    state.user = null
    clearToken()
    saveUser(null)
  }
}

export default auth
