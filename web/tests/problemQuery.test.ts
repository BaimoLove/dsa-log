import { describe, expect, it } from 'vitest'
import {
  countActiveFilters,
  DEFAULT_SORT,
  emptyFilter,
  hasActiveFilter,
  parseProblemFilter,
  serializeProblemFilter,
  toggleValue,
} from '@/utils/problemQuery'
import { isSearchHotkey, isTypingTarget } from '@/composables/useGlobalHotkeys'

describe('解析 URL 筛选', () => {
  it('空 query 得到默认筛选，不残留上一个页面的条件', () => {
    expect(parseProblemFilter({})).toEqual(emptyFilter())
    expect(parseProblemFilter({ kw: null, status: null, tags: undefined })).toEqual(emptyFilter())
  })

  it('关键词去首尾空格，其余维度按逗号拆开', () => {
    const f = parseProblemFilter({
      kw: '  二叉树 ',
      status: 'todo,solved',
      diff: 'easy,hard',
      tags: 't1,t2',
      sort: 'difficulty_asc',
    })
    expect(f.keyword).toBe('二叉树')
    expect(f.statuses).toEqual(['todo', 'solved'])
    expect(f.difficulties).toEqual(['easy', 'hard'])
    expect(f.tagIds).toEqual(['t1', 't2'])
    expect(f.sort).toBe('difficulty_asc')
  })

  it('同时接受逗号与重复 key 两种写法，并去重', () => {
    const f = parseProblemFilter({
      diff: ['easy', 'hard,easy'],
      tags: ['a', 'a', 'b'],
    })
    expect(f.difficulties).toEqual(['easy', 'hard'])
    expect(f.tagIds).toEqual(['a', 'b'])
  })

  it('非法枚举与空串被丢弃，不让手改的地址把页面搞白', () => {
    const f = parseProblemFilter({ status: 'done,,todo', diff: 'EASY,insane', tags: ', ,' })
    expect(f.statuses).toEqual(['todo'])
    expect(f.difficulties).toEqual([])
    expect(f.tagIds).toEqual([])
  })

  it('非法排序回落默认', () => {
    expect(parseProblemFilter({ sort: 'title_asc' }).sort).toBe(DEFAULT_SORT)
    expect(parseProblemFilter({ sort: '' }).sort).toBe(DEFAULT_SORT)
  })
})

describe('序列化 URL 筛选', () => {
  it('默认值与空维度不写进地址栏', () => {
    expect(serializeProblemFilter(emptyFilter())).toEqual({})
    const onlyKeyword = { ...emptyFilter(), keyword: '  两数之和  ' }
    expect(serializeProblemFilter(onlyKeyword)).toEqual({ kw: '两数之和' })
  })

  it('只写非默认排序', () => {
    expect(serializeProblemFilter({ ...emptyFilter(), sort: 'solvedAt_desc' })).toEqual({ sort: 'solvedAt_desc' })
  })

  it('序列化后再解析回到同一份筛选（刷新即复现）', () => {
    const original = {
      keyword: 'dp',
      statuses: ['solved' as const],
      difficulties: ['medium' as const, 'hard' as const],
      tagIds: ['t1', 't2'],
      sort: 'nextReviewAt_asc' as const,
    }
    expect(parseProblemFilter(serializeProblemFilter(original))).toEqual(original)
  })
})

describe('筛选计数', () => {
  it('按维度计，标签按个数计', () => {
    expect(countActiveFilters(emptyFilter())).toBe(0)
    expect(hasActiveFilter(emptyFilter())).toBe(false)

    const f = {
      keyword: 'x',
      statuses: ['todo' as const],
      difficulties: ['easy' as const],
      tagIds: ['a', 'b'],
      sort: 'solvedAt_desc' as const,
    }
    expect(countActiveFilters(f)).toBe(6)
    expect(hasActiveFilter(f)).toBe(true)
  })

  it('只选排序也算有筛选', () => {
    expect(hasActiveFilter({ ...emptyFilter(), sort: 'createdAt_asc' })).toBe(true)
  })
})

describe('toggleValue', () => {
  it('不存在则追加，存在则移除，保持原顺序', () => {
    expect(toggleValue(['a'], 'b')).toEqual(['a', 'b'])
    expect(toggleValue(['a', 'b'], 'a')).toEqual(['b'])
    expect(toggleValue([], 'x')).toEqual(['x'])
  })
})

describe('快捷键判定', () => {
  const key = (patch: Partial<KeyboardEvent>) => ({ key: '', ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, ...patch })

  it('Ctrl+K / ⌘+K 命中，带 Shift 或 Alt 不命中', () => {
    expect(isSearchHotkey(key({ key: 'k', ctrlKey: true }))).toBe(true)
    expect(isSearchHotkey(key({ key: 'K', metaKey: true }))).toBe(true)
    expect(isSearchHotkey(key({ key: 'k', ctrlKey: true, shiftKey: true }))).toBe(false)
    expect(isSearchHotkey(key({ key: 'k', ctrlKey: true, altKey: true }))).toBe(false)
    expect(isSearchHotkey(key({ key: 'k' }))).toBe(false)
  })

  it('输入控件与可编辑区域算「正在打字」', () => {
    const el = (tagName: string, isContentEditable = false) => ({ tagName, isContentEditable }) as unknown as HTMLElement
    expect(isTypingTarget(el('INPUT'))).toBe(true)
    expect(isTypingTarget(el('TEXTAREA'))).toBe(true)
    expect(isTypingTarget(el('SELECT'))).toBe(true)
    expect(isTypingTarget(el('DIV', true))).toBe(true)
    expect(isTypingTarget(el('DIV'))).toBe(false)
    expect(isTypingTarget(null)).toBe(false)
  })
})
