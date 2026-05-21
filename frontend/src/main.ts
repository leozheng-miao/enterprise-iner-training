import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { setAuthFailHandler } from './api/client'

import './styles/main.css'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  // 在装路由之前恢复 auth 状态，守卫才能拿到正确的 isLoggedIn
  const auth = useAuthStore()
  auth.bootFromStorage()

  // 注册 401/40110 跳登录回调（避免 client.ts → store 的循环 import）
  setAuthFailHandler(() => {
    if (auth.isLoggedIn) auth.logout()
    if (router.currentRoute.value.name !== 'login') {
      router.push({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  })

  app.use(router)
  app.use(ElementPlus)

  await router.isReady()
  app.mount('#app')
}

bootstrap()
