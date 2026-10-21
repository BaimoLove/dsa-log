import type { ListProgress } from '@/types/repository'
import { toPercent } from './progress'

/**
 * 题单看板的排序。放在 URL（`#/lists?sort=progress_asc`）而不是组件状态，
 * 与 M4 题库筛选同一套约定：刷新/从详情返回都还在。
 */
export const LIST_SORTS = ['default', 'progress_desc', 'progress_asc', 'name_asc'] as const
export type ListSort = (typeof LIST_SORTS)[number]

export const DEFAULT_LIST_SORT: ListSort = 'default'

export const LIST_SORT_LABEL: Record<ListSort, string> = {
  default: '默认顺序',
  progress_desc: '进度高 → 低',
  progress_asc: '进度低 → 高',
  name_asc: '名称 A → Z',
}

export function isListSort(value: unknown): value is ListSort {
  return typeof value === 'string' && (LIST_SORTS as readonly string[]).includes(value)
}

export function parseListSort(raw: string | null | undefined | (string | null)[]): ListSort {
  const values = Array.isArray(raw) ? raw : [raw]
  // 取第一个合法值，而不是第一个非空值——`?sort=bad&sort=name_asc` 也该认 name_asc
  const hit = values.find((v): v is ListSort => isListSort(v))
  return hit ?? DEFAULT_LIST_SORT
}

export function serializeListSort(sort: ListSort): Record<string, string> {
  return sort === DEFAULT_LIST_SORT ? {} : { sort }
}

function byName(a: ListProgress, b: ListProgress): number {
  return a.name.localeCompare(b.name, 'zh-Hans-CN') || a.id.localeCompare(b.id)
}

/**
 * 排序不改变入参（仓储已按 sortOrder 给好默认顺序）。
 *
 * 空题单在两种进度排序里都沉底：0% 和「没题」意义不同，
 * 把空题单排在最前面只会挡住真正该刷的题单。
 */
export function sortLists(lists: ListProgress[], sort: ListSort): ListProgress[] {
  const copy = [...lists]
  if (sort === 'default') return copy

  if (sort === 'name_asc') return copy.sort(byName)

  const direction = sort === 'progress_desc' ? -1 : 1
  return copy.sort((a, b) => {
    const aEmpty = a.total === 0
    const bEmpty = b.total === 0
    if (aEmpty !== bEmpty) return aEmpty ? 1 : -1
    const diff = toPercent(a.solved, a.total) - toPercent(b.solved, b.total)
    if (diff !== 0) return diff * direction
    return byName(a, b)
  })
}
