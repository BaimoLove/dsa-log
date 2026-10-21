/** 统一业务异常：视图层只看 code + message，不关心底层是 Dexie 还是 HTTP */
export class AppError extends Error {
  readonly code: string
  readonly cause?: unknown

  constructor(code: string, message: string, cause?: unknown) {
    super(message)
    this.name = 'AppError'
    this.code = code
    this.cause = cause
  }
}

export const ERROR_MESSAGE: Record<string, string> = {
  VALIDATION_FAILED: '参数不合法',
  NOT_FOUND: '数据不存在',
  CONFLICT: '数据冲突，请刷新后重试',
  UNDO_EXPIRED: '撤销已过期，可在列表中手动改回',
  UNDO_ALREADY_DONE: '该操作已撤销',
  PAYLOAD_TOO_LARGE: '单次操作数量超出上限',
  IMPORT_INVALID: '备份文件格式不正确',
  STORAGE_UNAVAILABLE: '浏览器本地存储不可用',
  INTERNAL_ERROR: '服务器内部错误',
}

export function toAppError(e: unknown): AppError {
  if (e instanceof AppError) return e
  if (e instanceof Error) return new AppError('INTERNAL_ERROR', e.message, e)
  return new AppError('INTERNAL_ERROR', String(e), e)
}
