import { AppError } from '@/types/common'

/**
 * HTTP 客户端：只做三件事——拼 URL、解 Result 包装、把失败收敛成 AppError。
 *
 * 为什么不直接用 fetch：全站返回体是 `{code,message,data,traceId}`，成功也要解一层；
 * 而「HTTP 200 + 业务失败」（比如 bulk 里部分 id 不合法）是常态，
 * 视图层不该同时判 res.ok 与 code。这里统一成「拿到 data 或抛 AppError」。
 */

/** 后端 Result.SUCCESS_CODE 的字面值，契约里只有这一个成功码 */
const SUCCESS_CODE = 'SUCCESS'

export type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

/** 查询参数允许数组（按重复 key 拼，Spring 的 List 参数认这种写法） */
export type QueryValue = string | number | boolean | null | undefined | ReadonlyArray<string | number>

export interface RequestOptions {
  method?: HttpMethod
  query?: Record<string, QueryValue>
  /** 会被 JSON.stringify；值为 undefined 的键自然被丢掉，正好对应「不传 = 不改」 */
  body?: unknown
  /** true 时返回原始文本（导出 CSV 用），不解析 Result 包装 */
  raw?: boolean
}

interface ResultEnvelope<T> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface ApiClientOptions {
  /** 例如 '/api'（走 vite 代理）或 'http://127.0.0.1:8080/api' */
  baseUrl: string
  /** 便于测试注入 */
  fetchImpl?: typeof fetch
  timeoutMs?: number
}

export interface ApiClient {
  request<T>(path: string, options?: RequestOptions): Promise<T>
  readonly baseUrl: string
}

/** HTTP 状态码兜底映射：后端返回体解析不出来时（网关/容器层的 HTML 错误页）用它 */
function codeFromStatus(status: number): string {
  switch (status) {
    case 400:
      return 'VALIDATION_FAILED'
    case 404:
      return 'NOT_FOUND'
    case 409:
      return 'CONFLICT'
    case 413:
      return 'PAYLOAD_TOO_LARGE'
    case 422:
      return 'IMPORT_INVALID'
    default:
      return 'INTERNAL_ERROR'
  }
}

function buildUrl(base: string, path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null) continue
    if (Array.isArray(value)) {
      // 空数组 = 这个条件不存在。拼成 `?tagIds=` 会让后端的 List 收到一个空串元素，
      // 于是「不筛选」变成「筛一个不存在的标签」，结果永远是空集
      for (const item of value) params.append(key, String(item))
      continue
    }
    // 空串同理：`?keyword=` 在 SQL 里会变成 LIKE '%%'，虽然等价但白白多一个条件分支
    if (typeof value === 'string' && value.trim() === '') continue
    params.append(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${base}${path}?${qs}` : `${base}${path}`
}

export function createApiClient(options: ApiClientOptions): ApiClient {
  const base = options.baseUrl.replace(/\/+$/, '')
  const fetchImpl = options.fetchImpl ?? globalThis.fetch
  const timeoutMs = options.timeoutMs ?? 15000

  async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
    const url = buildUrl(base, path, opts.query)
    // 15s 超时：本地 2G 机器上的慢查询也不该让界面无限转圈；
    // 超时走自己的错误码，别混进 INTERNAL_ERROR
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)

    let res: Response
    try {
      res = await fetchImpl(url, {
        method: opts.method ?? 'GET',
        headers: opts.body === undefined ? undefined : { 'Content-Type': 'application/json' },
        body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
        signal: controller.signal,
      })
    } catch (e) {
      if (e instanceof Error && e.name === 'AbortError') {
        throw new AppError('TIMEOUT', `请求超时（${timeoutMs}ms）：${path}`)
      }
      throw new AppError('NETWORK_ERROR', '连不上服务端，确认后端已启动、地址与端口正确', e)
    } finally {
      clearTimeout(timer)
    }

    if (opts.raw) {
      if (!res.ok) {
        throw new AppError(codeFromStatus(res.status), `导出失败（HTTP ${res.status}）`)
      }
      return (await res.text()) as T
    }

    let payload: ResultEnvelope<T> | null = null
    try {
      payload = (await res.json()) as ResultEnvelope<T>
    } catch {
      payload = null
    }

    if (!payload || typeof payload.code !== 'string') {
      // 例如 nginx 的 502 页、Spring 的默认 error 页：能连通但格式不对
      throw new AppError(codeFromStatus(res.status), `服务端返回了非预期内容（HTTP ${res.status}）`)
    }
    if (payload.code !== SUCCESS_CODE) {
      throw new AppError(payload.code, payload.message || `请求失败：${payload.code}`)
    }
    return payload.data
  }

  return { request, baseUrl: base }
}

export const apiBase = import.meta.env.VITE_API_BASE || '/api'

/** 默认实例：base 走 VITE_API_BASE，dev 下由 vite 代理到 8080 */
export const apiClient: ApiClient = createApiClient({ baseUrl: apiBase })
