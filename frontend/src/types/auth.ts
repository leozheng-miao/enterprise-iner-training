export type UserRole = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  nickname: string
  role: UserRole
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  user: User
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface RegisterResponse {
  id: number
  username: string
  nickname: string
  role: UserRole
}
