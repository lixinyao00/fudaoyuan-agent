<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="side-brand">
        <div class="logo">🛡️</div>
        <div class="brand-text">
          <strong>后台管理</strong>
          <small>{{ auth.displayName.value }}</small>
        </div>
      </div>

      <nav class="side-nav">
        <RouterLink to="/admin/knowledge" class="nav-item">
          <span class="nav-ico">📚</span> 知识库管理
        </RouterLink>
        <RouterLink to="/admin/monitor" class="nav-item">
          <span class="nav-ico">📊</span> 问答监控
        </RouterLink>
        <RouterLink to="/admin/config" class="nav-item">
          <span class="nav-ico">⚙️</span> 系统配置
        </RouterLink>
        <RouterLink to="/admin/crisis" class="nav-item">
          <span class="nav-ico">🚨</span> 危机告警
        </RouterLink>
      </nav>

      <div class="side-foot">
        <RouterLink to="/chat" class="foot-link">← 返回问答</RouterLink>
        <button class="logout" type="button" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import auth from '../stores/auth.js'

const router = useRouter()

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 248px 1fr;
  background: #f5f7fa;
  color: #2d3741;
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 22px 16px;
  background: #ffffff;
  border-right: 1px solid #e4e8ee;
}

.side-brand {
  display: flex; align-items: center; gap: 12px;
  padding: 6px 8px 18px;
  border-bottom: 1px solid #edf0f4;
  margin-bottom: 16px;
}
.logo {
  width: 40px; height: 40px; border-radius: 10px;
  display: grid; place-items: center; font-size: 19px;
  background: #eef2f7;
  color: #2f6df0;
}
.brand-text { display: flex; flex-direction: column; line-height: 1.3; }
.brand-text strong { color: #2d3741; font-size: 15px; }
.brand-text small { color: #8a96a3; font-size: 12px; }

.side-nav { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.nav-item {
  display: flex; align-items: center; gap: 11px;
  padding: 11px 13px; border-radius: 9px;
  color: #5b6775; font-size: 14px; font-weight: 600;
  transition: all 0.16s;
}
.nav-item:hover { background: #f0f4f8; color: #2d3741; }
.nav-item.router-link-active {
  background: #eaf1fe;
  color: #1f64da;
}
.nav-ico { font-size: 16px; }

.side-foot { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.foot-link {
  font-size: 13px; color: #6b7785; padding: 8px 13px;
  border-radius: 9px; transition: color 0.16s;
}
.foot-link:hover { color: #2d3741; }
.logout {
  padding: 10px; border: 1px solid #e6b8bb;
  background: #ffffff; color: #c0392b;
  border-radius: 9px; font-size: 13px; font-weight: 600;
  transition: all 0.16s;
}
.logout:hover { background: #fdeced; }

.admin-main { overflow-y: auto; padding: 28px clamp(20px, 4vw, 44px); }

@media (max-width: 760px) {
  .admin-shell { grid-template-columns: 1fr; }
  .sidebar { flex-direction: row; flex-wrap: wrap; align-items: center; gap: 8px; border-right: none; border-bottom: 1px solid #e4e8ee; }
  .side-brand { width: 100%; }
  .side-nav { flex-direction: row; flex-wrap: wrap; }
  .nav-item { flex: 1; justify-content: center; }
  .side-foot { flex-direction: row; width: 100%; }
  .foot-link, .logout { flex: 1; text-align: center; }
}
</style>
