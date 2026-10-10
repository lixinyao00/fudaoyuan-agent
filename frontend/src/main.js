import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import auth from './stores/auth.js'

import Chat from './pages/Chat.vue'
import Login from './pages/Login.vue'
import AdminLayout from './layouts/AdminLayout.vue'
import KnowledgeManage from './pages/admin/KnowledgeManage.vue'
import Monitor from './pages/admin/Monitor.vue'
import Config from './pages/admin/Config.vue'
import CrisisAlert from './pages/admin/CrisisAlert.vue'

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/chat', component: Chat, meta: { requiresAuth: true } },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: { name: 'admin-knowledge' } },
      { path: 'knowledge', name: 'admin-knowledge', component: KnowledgeManage },
      { path: 'monitor', name: 'admin-monitor', component: Monitor },
      { path: 'config', name: 'admin-config', component: Config },
      { path: 'crisis', name: 'admin-crisis', component: CrisisAlert }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/chat' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const loggedIn = auth.isLoggedIn.value
  const isAdmin = auth.isAdmin.value

  // 需要登录但未登录 -> 登录页
  if (to.meta.requiresAuth && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 需要管理员权限但角色不符 -> 学生聊天页
  if (to.meta.requiresAdmin && !isAdmin) {
    return { path: '/chat' }
  }
  // 已登录用户访问登录页 -> 按角色跳转
  if (to.path === '/login' && loggedIn) {
    return { path: isAdmin ? '/admin' : '/chat' }
  }
})

createApp(App).use(router).mount('#app')
