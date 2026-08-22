import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Chat from './pages/Chat.vue'
import Login from './pages/Login.vue'
import Admin from './pages/Admin.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/chat', component: Chat },
  { path: '/login', component: Login },
  { path: '/admin', component: Admin }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path === '/admin' && !localStorage.getItem('admin_token')) return '/login'
  return true
})

createApp(App).use(router).mount('#app')
