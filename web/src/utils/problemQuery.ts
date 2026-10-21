import { DIFFICULTIES, SORT_OPTIONS, STATUSES } from '@/types/enums'
import type { Difficulty, ProblemStatus, SortOption } from '@/types/enums'

/**
 * 题库页筛选条件在 URL 与 store 之间的唯一交换格式。
 *
 * 为什么以 URL 为准：刷新要保持筛选（M4 验收项），顺带白拿「从题目详情返回时
 * 筛选还在」，也不必再发明一套持久化（localStorage）与清理逻辑。
 * 筛选变化用 replace 而不是 push，避免每敲一次关键词就多一条历史记录。
 * 页码不进 URL——它属于浏览位置而非筛选条件，越界回退由 store 自己处理。
 */
export interface ProblemFilter {
  keyword: string
  statuses: ProblemStatus[]
  difficulties: Difficulty[]
  tagIds: string[]
  sort: SortOption
}

/** URL 键名取短名：可手改、可分享 */
export const FILTER_KEYS = {
  keyword: 'kw',
  status: 'status',
  difficulty: 'diff',
  tags: 'tags',
  sort: 'sort',
} as const

export const DEFAULT_SORT: SortOption = 'updatedAt_desc'

/** vue-router 的 LocationQuery 与手写对象都能喂进来 */
export type RawQuery = Record<string, string | null | undefined | (string | null)[]>

export function emptyFilter(): ProblemFilter {
  return { keyword: '', statuses: [], difficulties: [], tagIds: [], sort: DEFAULT_SORT }
}

function firstValue(raw: string | null | undefined | (string | null)[]): string {
  if (Array.isArray(raw)) {
    const hit = raw.find((v) => typeof v === 'string' && v !== '')
    return hit ?? ''
  }
  return raw ?? ''
}

/** 同时接受 `diff=easy,hard` 与 `diff=easy&diff=hard`，逗号分隔去空去重 */
function splitList(raw: string | null | undefined | (string | null)[]): string[] {
  const parts = Array.isArray(raw) ? raw : [raw]
  const out: string[] = []
  for (const part of parts) {
    if (typeof part !== 'string') continue
    for (const piece of part.split(',')) {
      const value = piece.trim()
      if (value && !out.includes(value)) out.push(value)
    }
  }
  return out
}

function keepAllowed<T extends string>(values: string[], allowed: readonly T[]): T[] {
  const set = new Set<string>(allowed)
  return values.filter((v): v is T => set.has(v))
}

function isSortOption(value: string): value is SortOption {
  return (SORT_OPTIONS as readonly string[]).includes(value)
}

/** 非法值一律丢弃回落默认，不在前端报错——手改 URL 不该白屏 */
export function parseProblemFilter(raw: RawQuery): ProblemFilter {
  const sortRaw = firstValue(raw[FILTER_KEYS.sort])
  return {
    keyword: firstValue(raw[FILTER_KEYS.keyword]).trim(),
    statuses: keepAllowed(splitList(raw[FILTER_KEYS.status]), STATUSES),
    difficulties: keepAllowed(splitList(raw[FILTER_KEYS.difficulty]), DIFFICULTIES),
    tagIds: splitList(raw[FILTER_KEYS.tags]),
    sort: isSortOption(sortRaw) ? sortRaw : DEFAULT_SORT,
  }
}

/** 默认值不写进 URL，保持地址栏干净 */
export function serializeProblemFilter(filter: ProblemFilter): Record<string, string> {
  const query: Record<string, string> = {}
  const keyword = filter.keyword.trim()
  if (keyword) query[FILTER_KEYS.keyword] = keyword
  if (filter.statuses.length) query[FILTER_KEYS.status] = filter.statuses.join(',')
  if (filter.difficulties.length) query[FILTER_KEYS.difficulty] = filter.difficulties.join(',')
  if (filter.tagIds.length) query[FILTER_KEYS.tags] = filter.tagIds.join(',')
  if (filter.sort !== DEFAULT_SORT) query[FILTER_KEYS.sort] = filter.sort
  return query
}

/**
 * 生效中的筛选维度数，用于按钮角标与「有没有筛选」判断。
 * 标签按个数计（选了 3 个标签就是 3 个筛选），其余维度按 1 计。
 */
export function countActiveFilters(filter: ProblemFilter): number {
  let count = 0
  if (filter.keyword.trim()) count += 1
  if (filter.statuses.length) count += 1
  if (filter.difficulties.length) count += 1
  count += filter.tagIds.length
  if (filter.sort !== DEFAULT_SORT) count += 1
  return count
}

export function hasActiveFilter(filter: ProblemFilter): boolean {
  return countActiveFilters(filter) > 0
}

/** 数组型筛选的通用增删，保持原顺序便于 URL 稳定 */
export function toggleValue<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value]
}
