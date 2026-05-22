/**
 * 后端统一响应包装。
 * 所有非 SSE 接口都返回这个结构。
 */
export interface BaseResponse<T> {
  code: number
  data: T | null
  message: string
}

/**
 * 错误码（与后端 com.leo.enterpriseinertraining.exception.ErrorCode 对齐）。
 */
export const ErrorCode = {
  SUCCESS: 0,
  PARAMS_ERROR: 40000,
  USERNAME_EXISTS: 40010,
  USER_NOT_FOUND: 40011,
  BAD_CREDENTIALS: 40012,
  NOT_LOGIN: 40100,
  NO_AUTH: 40101,
  JWT_INVALID: 40110,
  FORBIDDEN: 40300,
  NOT_FOUND: 40400,
  TOO_MANY_REQUESTS: 42900,
  SYSTEM_ERROR: 50000
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/**
 * 触发自动跳登录的错误码集合（401/jwt 失效）。
 */
export const AUTH_FAIL_CODES: ReadonlyArray<number> = [
  ErrorCode.NOT_LOGIN,
  ErrorCode.JWT_INVALID
]

/**
 * 业务异常（response.code !== 0 时由拦截器抛出）。
 */
export class BizError extends Error {
  constructor(
    public readonly code: number,
    public readonly bizMessage: string
  ) {
    super(bizMessage)
    this.name = 'BizError'
  }
}
