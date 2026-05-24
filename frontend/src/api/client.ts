import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import {
  AUTH_FAIL_CODES,
  BizError,
  ErrorCode,
  type BaseResponse
} from '@/types/api'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' }
})

// ===== 请求拦截器：注入 JWT =====
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ===== 响应拦截器：解 BaseResponse + 错误分发 =====
// 用一个延迟解析的回调避免循环 import（client.ts 不直接 import authStore）
let onAuthFail: (() => void) | null = null
export function setAuthFailHandler(handler: () => void): void {
  onAuthFail = handler
}

instance.interceptors.response.use(
  (resp: AxiosResponse<BaseResponse<unknown>>) => {
    const body = resp.data
    if (!body || typeof body.code !== 'number') {
      // 非标准响应（如 SSE 直返），原样返回
      return resp
    }

    if (body.code === ErrorCode.SUCCESS) {
      // 把 data 直接放到 resp.data，调用方拿到的就是 T
      return { ...resp, data: body.data } as AxiosResponse<unknown>
    }

    // 业务失败
    const err = new BizError(body.code, body.message || '请求失败')

    if (AUTH_FAIL_CODES.includes(body.code)) {
      onAuthFail?.()
    } else {
      ElMessage.warning(err.bizMessage)
    }
    return Promise.reject(err)
  },
  (error) => {
    // 网络错误 / HTTP 非 2xx
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      onAuthFail?.()
      return Promise.reject(new BizError(status, '登录已失效，请重新登录'))
    }
    const msg = error?.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/**
 * 类型化的 GET / POST 封装。
 * 拦截器已把 BaseResponse.data 平铺出来，所以这里返回 T。
 */
export async function http<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await instance.request<T>(config)
  return resp.data as T
}

export const apiGet = <T>(url: string, params?: object) =>
  http<T>({ method: 'GET', url, params })

export const apiPost = <T>(url: string, data?: object, params?: object) =>
  http<T>({ method: 'POST', url, data, params })

export const apiPut = <T>(url: string, data?: object) =>
  http<T>({ method: 'PUT', url, data })

export const apiClient = instance
