import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { User } from '@/types/auth'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 从 localStorage 恢复（在 main.ts 应用启动时调用一次）。 */
  function bootFromStorage(): void {
    const t = localStorage.getItem(TOKEN_KEY)
    const u = localStorage.getItem(USER_KEY)
    if (t) token.value = t
    if (u) {
      try {
        user.value = JSON.parse(u) as User
      } catch {
        user.value = null
        localStorage.removeItem(USER_KEY)
      }
    }
  }

  function setSession(payload: { token: string; user: User }): void {
    token.value = payload.token
    user.value = payload.user
    localStorage.setItem(TOKEN_KEY, payload.token)
    localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
  }

  function setUser(u: User): void {
    user.value = u
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  function logout(): void {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    bootFromStorage,
    setSession,
    setUser,
    logout
  }
})
