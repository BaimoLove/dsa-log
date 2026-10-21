import { describe, expect, it } from 'vitest'
import type { ListProgress } from '@/types/repository'
import {
  DEFAULT_LIST_SORT,
  LIST_SORTS,
  parseListSort,
  serializeListSort,
  sortLists,
} from '@/utils/listSort'

function list(id: string, name: string, total: number, solved: number): ListProgress {
  return { id, name, description: '', color: '#6366f1', sortOrder: 0, total, solved }
}

const A = list('a', '动态规划', 30, 12)
const B = list('b', '图论', 10, 10)
const C = list('c', '字符串', 20, 0)
const D = list('d', '空题单', 0, 0)

const ids = (items: ListProgress[]) => items.map((l) => l.id)

describe('题单排序', () => {
  it('默认顺序保持仓储给的顺序，不改原数组', () => {
    const input = [A, B, C, D]
    const out = sortLists(input, 'default')
    expect(ids(out)).toEqual(['a', 'b', 'c', 'd'])
    expect(out).not.toBe(input)
  })

  it('进度高到低，空题单沉底', () => {
    // B 100% → A 40% → C 0% → D 空
    expect(ids(sortLists([D, C, A, B], 'progress_desc'))).toEqual(['b', 'a', 'c', 'd'])
  })

  it('进度低到高，空题单同样沉底（不因为有 0% 就排最前）', () => {
    expect(ids(sortLists([D, B, A, C], 'progress_asc'))).toEqual(['c', 'a', 'b', 'd'])
  })

  it('进度相同按名称排，结果稳定', () => {
    const x = list('x1', 'B 题单', 10, 5)
    const y = list('x2', 'A 题单', 10, 5)
    expect(ids(sortLists([x, y], 'progress_desc'))).toEqual(['x2', 'x1'])
  })

  it('名称排序按字典序（用全小写 ASCII 名，避开 ICU 大小写与拼音差异）', () => {
    const n1 = list('n1', 'beta', 10, 5)
    const n2 = list('n2', 'alpha', 10, 5)
    const n3 = list('n3', 'gamma', 10, 5)
    expect(ids(sortLists([n1, n2, n3], 'name_asc'))).toEqual(['n2', 'n1', 'n3'])
  })
})

describe('排序的 URL 约定', () => {
  it('默认值不写进地址栏', () => {
    expect(serializeListSort(DEFAULT_LIST_SORT)).toEqual({})
    expect(serializeListSort('progress_asc')).toEqual({ sort: 'progress_asc' })
  })

  it('非法值回落默认，数组取首个合法值', () => {
    expect(parseListSort('nope')).toBe(DEFAULT_LIST_SORT)
    expect(parseListSort(null)).toBe(DEFAULT_LIST_SORT)
    expect(parseListSort([])).toBe(DEFAULT_LIST_SORT)
    expect(parseListSort(['bad', 'name_asc'])).toBe('name_asc')
  })

  it('每个枚举都有中文标签（列表里有新增就会被这条挡住）', () => {
    expect(LIST_SORTS.length).toBe(4)
    for (const s of LIST_SORTS) expect(parseListSort(s)).toBe(s)
  })
})
