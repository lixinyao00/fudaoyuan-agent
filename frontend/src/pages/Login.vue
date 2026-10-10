<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">🎓</div>
        <h1>辅导员AI问答助手</h1>
        <p>登录后继续使用</p>
      </div>

      <div class="tabs" role="tablist">
        <button
          class="tab"
          :class="{ active: roleTab === 'STUDENT' }"
          type="button"
          role="tab"
          @click="switchTab('STUDENT')"
        >学生登录</button>
        <button
          class="tab"
          :class="{ active: roleTab === 'ADMIN' }"
          type="button"
          role="tab"
          @click="switchTab('ADMIN')"
        >管理员登录</button>
      </div>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">用户名</span>
          <input
            v-model.trim="username"
            type="text"
            name="username"
            autocomplete="username"
            placeholder="请输入用户名"
            :disabled="loading"
          />
        </label>
        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="password"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="loading"
          />
        </label>

        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

        <button class="submit" type="submit" :disabled="loading || !username || !password">
          {{ loading ? '登录中...' : (roleTab === 'ADMIN' ? '管理员登录' : '学生登录') }}
        </button>
      </form>

      <p class="hint">请使用已配置的账号登录</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import auth from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const roleTab = ref('STUDENT')
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

function switchTab(tab) {
  if (loading.value) return
  roleTab.value = tab
  errorMsg.value = ''
}

async function submit() {
  if (loading.value || !username.value || !password.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const user = await auth.login(username.value, password.value)
    const role = user?.role || ''
    if (roleTab.value === 'ADMIN' && role !== 'ADMIN') {
      auth.logout()
      errorMsg.value = '该账号不是管理员，请切换到学生登录'
      return
    }
    if (roleTab.value === 'STUDENT' && role === 'ADMIN') {
      // 管理员账号在学生 tab 登录也允许，直接进后台
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect && redirect.startsWith('/')) {
      router.replace(redirect)
    } else {
      router.replace(role === 'ADMIN' ? '/admin' : '/chat')
    }
  } catch (e) {
    errorMsg.value = e?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f5f7fa;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border: 1px solid #e4e8ee;
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 12px 32px rgba(40, 56, 74, 0.07);
}

.brand { text-align: center; margin-bottom: 26px; }
.brand-icon {
  display: grid; place-items: center;
  width: 56px; height: 56px;
  margin: 0 auto 14px;
  border-radius: 14px;
  font-size: 26px;
  background: #eef2f7;
  color: #2f6df0;
}
.brand h1 { margin: 0; color: #2d3741; font-size: 20px; letter-spacing: -0.01em; }
.brand p { margin: 6px 0 0; color: #8a96a3; font-size: 13px; }

.tabs {
  display: grid; grid-template-columns: 1fr 1fr; gap: 6px;
  background: #f0f2f5;
  border: 1px solid #e4e8ee;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 20px;
}
.tab {
  padding: 9px 0;
  border: 0; border-radius: 8px;
  background: transparent;
  color: #6b7785;
  font-size: 14px; font-weight: 600;
  transition: all 0.18s;
}
.tab:hover { color: #2d3741; }
.tab.active {
  color: #1f64da;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(40, 56, 74, 0.1);
}

.form { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field-label { color: #6b7785; font-size: 12px; font-weight: 600; }
.field input {
  padding: 11px 13px;
  border: 1px solid #d8dee6;
  border-radius: 9px;
  background: #fbfcfd;
  color: #2d3741;
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.field input::placeholder { color: #a8b2bd; }
.field input:focus {
  border-color: #2f6df0;
  box-shadow: 0 0 0 3px rgba(47, 109, 240, 0.12);
}
.field input:disabled { opacity: 0.6; background: #f3f5f8; }

.error {
  margin: 0;
  padding: 9px 12px;
  border-radius: 9px;
  background: #fdeced;
  border: 1px solid #f4c4c8;
  color: #c0392b;
  font-size: 13px;
}

.submit {
  margin-top: 4px;
  padding: 12px;
  border: 0; border-radius: 10px;
  background: #2f6df0;
  color: #fff; font-size: 15px; font-weight: 700;
  transition: background 0.18s, transform 0.18s;
}
.submit:hover:not(:disabled) { background: #2360db; transform: translateY(-1px); }
.submit:disabled { background: #b7c4d6; color: #f2f5f9; cursor: not-allowed; }

.hint { margin: 22px 0 0; text-align: center; color: #8a96a3; font-size: 12px; }

.demo-accounts { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
.demo-account {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border: 1px solid #e4e8ee;
  border-radius: 9px;
  background: #f8fafc;
  font-size: 13px;
  transition: border-color 0.18s, background 0.18s;
}
.demo-account.active {
  border-color: #2f6df0;
  background: #eef4ff;
}
.demo-role { color: #4a5768; font-weight: 600; }
.demo-account.active .demo-role { color: #1f64da; }
.demo-creds { color: #6b7785; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }

@media (max-width: 480px) {
  .login-card { padding: 28px 20px 22px; }
}
</style>
