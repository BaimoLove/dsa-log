import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { db } from '@/db'
import { problemRepo } from '@/repositories'
import { useProblemStore } from '@/stores/problem'
import { dexieTagRepository as tagRepo } from '@/repositories/dexie/tagRepository'
import { parseProblemFilter, serializeProblemFilter } from '@/utils/problemQuery'

/**
 * M4 验收的链路级用例：URL query → parse → store 镜像 → 仓储查询。
 * 页面里的 watch 做的正是这三步，所以这里等价于「地址栏长这样，列表该出什么」。
 */
async function listByUrl(query: Record<string, string>): Promise<{ ids: string[]; total: number }> {
  const store = useProblemStore()
  store.applyFilter(parseProblemFilter(query))
  store.setPage(1)
  const res = await problemRepo.list({ ...store.query, page: store.page, size: store.size })
  return { ids: res.items.map((p) => p.id), total: res.total }
}

beforeEach(async () => {
  setActivePinia(createPinia())
  await db.open()
  await Promise.all([db.problems.clear(), db.tags.clear(), db.reviews.clear(), db.listItems.clear()])
})

async function seed() {
  const a = await tagRepo.create('DP', '#6366f1')
  const b = await tagRepo.create('背包', '#f59e0b')

  const p1 = await problemRepo.create({ title: 'dp 背包', difficulty: 'easy', tagIds: [a.id, b.id] })
  const p2 = await problemRepo.create({ title: 'dp 区间', difficulty: 'easy', tagIds: [a.id] })
  const p3 = await problemRepo.create({ title: 'dp 树形', difficulty: 'hard', tagIds: [a.id, b.id] })
  await problemRepo.create({ title: '背包 II', difficulty: 'medium', tagIds: [b.id] })
  return { a, b, p1, p2, p3 }
}

describe('URL 筛选链路', () => {
  it('多标签是交集，不是并集', async () => {
    const { a, b, p1, p3 } = await seed()
    const res = await listByUrl({ tags: `${a.id},${b.id}` })
    expect(new Set(res.ids)).toEqual(new Set([p1.id, p3.id]))
    expect(res.total).toBe(2)
  })

  it('标签 ∩ 难度 ∩ 关键词 三层叠加', async () => {
    const { a, b, p1 } = await seed()
    const onlyEasy = await listByUrl({ tags: `${a.id},${b.id}`, diff: 'easy' })
    expect(onlyEasy.ids).toEqual([p1.id])

    const withKeyword = await listByUrl({ tags: `${a.id},${b.id}`, diff: 'easy', kw: '背包' })
    expect(withKeyword.ids).toEqual([p1.id])

    const missKeyword = await listByUrl({ tags: `${a.id},${b.id}`, diff: 'easy', kw: '区间' })
    expect(missKeyword.ids).toEqual([])
    expect(missKeyword.total).toBe(0)
  })

  it('刷新等价于重新解析同一串 URL，结果一致', async () => {
    const { a, b } = await seed()
    const first = await listByUrl({ tags: `${a.id},${b.id}`, sort: 'difficulty_asc' })
    // 页面把状态写回地址栏，刷新后再读回来
    const roundTrip = parseProblemFilter(
      serializeProblemFilter({
        keyword: '',
        statuses: [],
        difficulties: [],
        tagIds: [a.id, b.id],
        sort: 'difficulty_asc',
      }),
    )
    const second = await listByUrl(serializeProblemFilter(roundTrip))
    expect(second.ids).toEqual(first.ids)
  })

  it('状态维度来自地址栏：已刷 + 标签后只剩刷过的那道', async () => {
    const { a, b, p1 } = await seed()
    await problemRepo.markSolved(p1.id)
    const res = await listByUrl({ status: 'solved', tags: `${a.id},${b.id}` })
    expect(res.ids).toEqual([p1.id])
  })

  it('非法 URL 值不会把列表打成空', async () => {
    await seed()
    const res = await listByUrl({ diff: 'insane', status: 'done', sort: 'nope' })
    expect(res.total).toBe(4)
  })

  it('applyFilter 会整体覆盖，不残留上一次的条件', async () => {
    const { a, b } = await seed()
    const store = useProblemStore()
    store.applyFilter(parseProblemFilter({ kw: '区间', diff: 'hard', tags: `${a.id},${b.id}` }))
    store.applyFilter(parseProblemFilter({}))
    expect(store.query).toEqual({
      keyword: '',
      statuses: [],
      difficulties: [],
      tagIds: [],
      sort: 'updatedAt_desc',
    })
  })
})
