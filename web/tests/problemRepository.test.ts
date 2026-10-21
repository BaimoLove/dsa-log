import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { dexieProblemRepository as repo } from '@/repositories/dexie/problemRepository'
import { dexieTagRepository as tags } from '@/repositories/dexie/tagRepository'
import { DAY_MS } from '@/utils/date'

beforeEach(async () => {
  await db.open()
  await Promise.all([db.problems.clear(), db.tags.clear(), db.reviews.clear(), db.listItems.clear(), db.problemLists.clear()])
})

describe('题目 CRUD', () => {
  it('新建后默认未刷，时间戳为当前时刻', async () => {
    const before = Date.now()
    const p = await repo.create({ title: '  两数之和  ', difficulty: 'easy', source: 'LeetCode' })
    expect(p.title).toBe('两数之和')
    expect(p.status).toBe('todo')
    expect(p.solveCount).toBe(0)
    expect(p.solvedAt).toBeNull()
    expect(p.createdAt).toBeGreaterThanOrEqual(before)
    expect(p.tags).toEqual([])
  })

  it('标题为空拒绝创建', async () => {
    await expect(repo.create({ title: '   ', difficulty: 'easy' })).rejects.toThrow('标题不能为空')
  })

  it('更新标题与标签，[] 表示清空', async () => {
    const t = await tags.create('DP', '#6366f1')
    const p = await repo.create({ title: '爬楼梯', difficulty: 'easy', tagIds: [t.id] })
    expect(p.tagIds).toEqual([t.id])

    const cleared = await repo.update(p.id, { tagIds: [] })
    expect(cleared.tagIds).toEqual([])

    const renamed = await repo.update(p.id, { title: '爬楼梯 II' })
    expect(renamed.title).toBe('爬楼梯 II')
  })

  it('标签改名后，题目上显示的是新名字（tags 不落库）', async () => {
    const t = await tags.create('动态规划', '#6366f1')
    const p = await repo.create({ title: '最长递增子序列', difficulty: 'medium', tagIds: [t.id] })
    expect(p.tags[0]?.name).toBe('动态规划')

    await tags.update(t.id, { name: 'DP' })
    const fresh = await repo.getById(p.id)
    expect(fresh?.tags[0]?.name).toBe('DP')
  })

  it('删除级联清掉 reviews 与 listItems', async () => {
    const p = await repo.create({ title: '三数之和', difficulty: 'medium' })
    await repo.markSolved(p.id)
    await db.problemLists.add({
      id: 'list-1',
      name: 'Hot100',
      description: '',
      color: '#6366f1',
      sortOrder: 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
    await repo.bulkAddToList([p.id], 'list-1')
    expect(await db.reviews.where('problemId').equals(p.id).count()).toBe(1)
    expect(await db.listItems.where('problemId').equals(p.id).count()).toBe(1)

    await repo.remove(p.id)
    expect(await db.problems.get(p.id)).toBeUndefined()
    expect(await db.reviews.where('problemId').equals(p.id).count()).toBe(0)
    expect(await db.listItems.where('problemId').equals(p.id).count()).toBe(0)
  })
})

describe('标记已刷', () => {
  it('首次标记写入 solvedAt、进入复习池（次日）、插一条流水', async () => {
    const p = await repo.create({ title: '两数之和', difficulty: 'easy' })
    const before = Date.now()
    const res = await repo.markSolved(p.id)
    const after = Date.now()

    expect(res.firstSolve).toBe(true)
    expect(res.problem.status).toBe('solved')
    expect(res.problem.solvedAt).toBeGreaterThanOrEqual(before)
    expect(res.problem.solvedAt).toBeLessThanOrEqual(after)
    expect(res.problem.solveCount).toBe(1)
    expect(res.problem.reviewStage).toBe(0)
    expect(res.problem.nextReviewAt).toBe((res.problem.solvedAt as number) + DAY_MS)
    expect(res.nextReviewAt).toBe(res.problem.nextReviewAt)

    const records = await db.reviews.where('problemId').equals(p.id).toArray()
    expect(records).toHaveLength(1)
    expect(records[0].kind).toBe('MARK_SOLVED')
  })

  it('重复标记只加 solveCount，solvedAt 与复习排期不变', async () => {
    const p = await repo.create({ title: '两数之和', difficulty: 'easy' })
    const first = await repo.markSolved(p.id)
    const second = await repo.markSolved(p.id)

    expect(second.firstSolve).toBe(false)
    expect(second.problem.solveCount).toBe(2)
    expect(second.problem.solvedAt).toBe(first.problem.solvedAt)
    expect(second.problem.nextReviewAt).toBe(first.problem.nextReviewAt)
    expect(await db.reviews.where('problemId').equals(p.id).count()).toBe(2)
  })

  it('取消已刷保留历史，但退出复习池', async () => {
    const p = await repo.create({ title: '两数之和', difficulty: 'easy' })
    const solved = await repo.markSolved(p.id)
    const back = await repo.markUnsolved(p.id)

    expect(back.status).toBe('todo')
    expect(back.nextReviewAt).toBeNull()
    expect(back.reviewStage).toBe(0)
    expect(back.solvedAt).toBe(solved.problem.solvedAt)
    expect(back.solveCount).toBe(1)
    expect(await db.reviews.where('problemId').equals(p.id).count()).toBe(2)
  })

  it('批量标记已刷返回影响行数', async () => {
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'hard' })
    const res = await repo.bulkMarkSolved([a.id, b.id, 'not-exist'])
    expect(res.affected).toBe(2)
    expect((await repo.getById(b.id))?.status).toBe('solved')
  })
})

describe('查询与筛选', () => {
  it('多标签是 AND：必须同时命中', async () => {
    const dp = await tags.create('DP', '#6366f1')
    const array = await tags.create('数组', '#0ea5e9')
    const both = await repo.create({ title: '打家劫舍', difficulty: 'medium', tagIds: [dp.id, array.id] })
    await repo.create({ title: '爬楼梯', difficulty: 'easy', tagIds: [dp.id] })

    const onlyDp = await repo.list({ tagIds: [dp.id] })
    expect(onlyDp.total).toBe(2)

    const and = await repo.list({ tagIds: [dp.id, array.id] })
    expect(and.total).toBe(1)
    expect(and.items[0].id).toBe(both.id)
  })

  it('关键词匹配标题/思路/备注/来源，忽略大小写', async () => {
    await repo.create({ title: 'Two Sum', difficulty: 'easy' })
    await repo.create({ title: '三数之和', difficulty: 'medium', note: '双指针容易漏边界' })

    expect((await repo.list({ keyword: 'two' })).total).toBe(1)
    expect((await repo.list({ keyword: '指针' })).total).toBe(1)
  })

  it('分页从 1 起，两页不重复', async () => {
    for (let i = 0; i < 5; i += 1) {
      await repo.create({ title: `题 ${i}`, difficulty: 'easy' })
    }
    const p1 = await repo.list({ page: 1, size: 2 })
    const p2 = await repo.list({ page: 2, size: 2 })
    expect(p1.total).toBe(5)
    expect(p1.totalPages).toBe(3)
    expect(p1.items).toHaveLength(2)
    expect(p2.items).toHaveLength(2)
    const ids = new Set([...p1.items, ...p2.items].map((p) => p.id))
    expect(ids.size).toBe(4)
  })

  it('状态筛选与难度筛选', async () => {
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    await repo.create({ title: 'B', difficulty: 'hard' })
    await repo.markSolved(a.id)

    expect((await repo.list({ statuses: ['solved'] })).total).toBe(1)
    expect((await repo.list({ difficulties: ['hard'] })).total).toBe(1)
  })
})

describe('随机抽题（仓储层）', () => {
  it('优先抽未刷；全部已刷时回落到全库', async () => {
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const solved = await repo.create({ title: 'S', difficulty: 'hard' })
    await repo.markSolved(solved.id)

    for (let i = 0; i < 10; i += 1) {
      const pick = await repo.random([])
      expect(pick?.problem.id).toBe(a.id)
      expect(pick?.pool).toBe('unsolved')
    }

    await repo.markSolved(a.id)
    const fallback = await repo.random([])
    expect(fallback?.pool).toBe('all')
    expect(fallback?.problem.status).toBe('solved')
  })

  it('空库返回 null', async () => {
    expect(await repo.random([])).toBeNull()
  })
})
