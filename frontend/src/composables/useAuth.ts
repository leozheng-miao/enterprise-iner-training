import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

/**
 * 包一层 useRouter + ElMessage，让 LoginView/RegisterView 调起来更短。
 */
export function useAuth() {
  const router = useRouter()
  const store = useAuthStore()

  async function login(body: LoginRequest, redirect?: string): Promise<void> {
    const resp = await userApi.login(body)
    store.setSession(resp)
    ElMessage.success(`欢迎回来，${resp.user.nickname || resp.user.username}`)
    await router.push(redirect || '/')
  }

  async function register(body: RegisterRequest): Promise<void> {
    await userApi.register(body)
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  }

  async function refreshMe(): Promise<void> {
    const u = await userApi.me()
    store.setUser(u)
  }

  function logout(): void {
    store.logout()
    router.push('/login')
  }

  return { store, login, register, refreshMe, logout }
}
