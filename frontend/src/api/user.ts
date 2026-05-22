import { apiGet, apiPost } from './client'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  User
} from '@/types/auth'

export const userApi = {
  register(body: RegisterRequest) {
    return apiPost<RegisterResponse>('/user/register', body)
  },
  login(body: LoginRequest) {
    return apiPost<LoginResponse>('/user/login', body)
  },
  me() {
    return apiGet<User>('/user/me')
  }
}
