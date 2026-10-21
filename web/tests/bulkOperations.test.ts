import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { dexieProblemRepository as repo } from '@/repositories/dexie/problemRepository'
import { dexieTagRepository as tags } from '@/repositories/dexie/tagRepository'
import { dexieListRepository as lists } from '@/repositories/dexie/listRepository'

beforeEach(async () => {
  await db.open()
  await Promise.all([
    db.problems.clear(),
    db.tags.clear(),
    db.reviews.clear(),
    db.listItems.clear(),
    db.problemLists.clear(),
  ])
})

describe('批量标记已刷', () => {
  it('只统计真实存在的题目，affected 与命中数一致', async () => {
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'medium' })

    const res = await repo.bulkMarkSolved([a.id, b.id, 'not-exist'])

    expect(res.affected).toBe(2)
  })

  it('首次刷写入 solvedAt 与复习排期，并留一条流水', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })

    await repo.bulkMarkSolved([p.id])

    const solved = await repo.getById(p.id)
    expect(solved?.status).toBe('solved')
    expect(solved?.solveCount).toBe(1)
    expect(solved?.solvedAt).not.toBeNull()
    expect(solved?.nextReviewAt).not.toBeNull()
    expect(await db.reviews.count()).toBe(1)
  })

  it('重复刷只累加次数，不动已定的复习排期', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    await repo.markSolved(p.id)
    const first = await repo.getById(p.id)

    await repo.bulkMarkSolved([p.id])

    const second = await repo.getById(p.id)
    expect(second?.solveCount).toBe(2)
    expect(second?.solvedAt).toBe(first?.solvedAt)
    expect(second?.nextReviewAt).toBe(first?.nextReviewAt)
  })

  it('没有命中任何行时 affected=0', async () => {
    expect((await repo.bulkMarkSolved(['not-exist'])).affected).toBe(0)
  })
})

describe('批量改回未刷（批量已刷的相反操作）', () => {
  it('回到 todo 并清空排期，但保留 solvedAt 与 solveCount', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    await repo.markSolved(p.id)

    const res = await repo.bulkMarkUnsolved([p.id])
    expect(res.affected).toBe(1)

    const back = await repo.getById(p.id)
    expect(back?.status).toBe('todo')
    expect(back?.nextReviewAt).toBeNull()
    expect(back?.reviewStage).toBe(0)
    // 历史事实不清零：刷过就是刷过
    expect(back?.solvedAt).not.toBeNull()
    expect(back?.solveCount).toBe(1)
  })

  it('已是未刷状态的题目跳过，不计入 affected', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    expect((await repo.bulkMarkUnsolved([p.id])).affected).toBe(0)
    expect(await db.reviews.count()).toBe(0)
  })

  it('与批量已刷互为逆操作：来回一次后状态一致', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })

    await repo.bulkMarkSolved([p.id])
    await repo.bulkMarkUnsolved([p.id])
    await repo.bulkMarkSolved([p.id])

    const final = await repo.getById(p.id)
    expect(final?.status).toBe('solved')
    expect(final?.solveCount).toBe(2)
  })
})

describe('批量打标签', () => {
  it('追加而不是覆盖已有标签', async () => {
    const keep = await tags.create('数组', '#0ea5e9')
    const added = await tags.create('DP', '#6366f1')
    const p = await repo.create({ title: 'A', difficulty: 'easy', tagIds: [keep.id] })

    const res = await repo.bulkAddTags([p.id], [added.id])

    expect(res.affected).toBe(1)
    expect((await repo.getById(p.id))?.tagIds).toEqual([keep.id, added.id])
  })

  it('已含该标签时为幂等：affected=0 且 updatedAt 不变', async () => {
    const t = await tags.create('DP', '#6366f1')
    const p = await repo.create({ title: 'A', difficulty: 'easy', tagIds: [t.id] })
    const before = await repo.getById(p.id)

    const res = await repo.bulkAddTags([p.id], [t.id])

    expect(res.affected).toBe(0)
    expect((await repo.getById(p.id))?.updatedAt).toBe(before?.updatedAt)
  })
})

describe('批量加入题单', () => {
  it('加入后能按加入顺序读出，重复加入 affected=0', async () => {
    const list = await lists.create({ name: 'DP 30 题' })
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'easy' })

    const first = await repo.bulkAddToList([a.id, b.id], list.id)
    const second = await repo.bulkAddToList([a.id], list.id)

    expect(first.affected).toBe(2)
    expect(second.affected).toBe(0)
    expect(await lists.problemIdsOf(list.id)).toEqual([a.id, b.id])
  })
})

describe('批量删除', () => {
  it('连带清掉复习流水与题单关联，affected 只算真实存在的行', async () => {
    const list = await lists.create({ name: 'DP' })
    const p = await repo.create({ title: '要删的题', difficulty: 'hard' })
    await repo.markSolved(p.id)
    await repo.bulkAddToList([p.id], list.id)

    const res = await repo.bulkRemove([p.id, 'not-exist'])

    expect(res.affected).toBe(1)
    expect(await repo.getById(p.id)).toBeNull()
    expect(await db.reviews.count()).toBe(0)
    expect(await lists.problemIdsOf(list.id)).toEqual([])
  })
})
