import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { dexieProblemRepository as repo } from '@/repositories/dexie/problemRepository'
import { dexieTagRepository as tags } from '@/repositories/dexie/tagRepository'
import { dexieListRepository as lists } from '@/repositories/dexie/listRepository'
import {
  dexieUndoRepository as undoRepo,
  registerUndo,
  pendingUndoCount,
  resetUndoRegistry,
} from '@/repositories/dexie/undoRegistry'

beforeEach(async () => {
  await db.open()
  await Promise.all([
    db.problems.clear(),
    db.tags.clear(),
    db.reviews.clear(),
    db.listItems.clear(),
    db.problemLists.clear(),
  ])
  resetUndoRegistry()
})

function opIdOf(res: { opId?: string }): string {
  if (!res.opId) throw new Error('批量操作没有返回 opId')
  return res.opId
}

describe('批量操作返回撤销令牌', () => {
  it('返回 affected 与 opId，且截止时间在未来', async () => {
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'medium' })

    const res = await repo.bulkMarkSolved([a.id, b.id, 'not-exist'])

    expect(res.affected).toBe(2)
    expect(res.opId).toBeTruthy()
    expect(res.undoExpiresAt).toBeGreaterThan(Date.now())
  })

  it('没有任何命中时不登记快照', async () => {
    const res = await repo.bulkMarkSolved(['not-exist'])
    expect(res.affected).toBe(0)
    expect(res.opId).toBeUndefined()
    expect(pendingUndoCount()).toBe(0)
  })

  it('批量打标签时若已含该标签，不计入 affected 也不登记快照', async () => {
    const t = await tags.create('DP', '#6366f1')
    const p = await repo.create({ title: 'A', difficulty: 'easy', tagIds: [t.id] })

    const res = await repo.bulkAddTags([p.id], [t.id])

    expect(res.affected).toBe(0)
    expect(res.opId).toBeUndefined()
  })
})

describe('批量已刷的撤销', () => {
  it('还原 status / solveCount / solvedAt / nextReviewAt 并删掉本次流水', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })

    const res = await repo.bulkMarkSolved([p.id])
    const solved = await repo.getById(p.id)
    expect(solved?.status).toBe('solved')
    expect(solved?.solveCount).toBe(1)
    expect(solved?.solvedAt).not.toBeNull()
    expect(await db.reviews.count()).toBe(1)

    const undone = await undoRepo.undo(opIdOf(res))
    expect(undone.affected).toBe(1)

    const restored = await repo.getById(p.id)
    expect(restored?.status).toBe('todo')
    expect(restored?.solveCount).toBe(0)
    expect(restored?.solvedAt).toBeNull()
    expect(restored?.nextReviewAt).toBeNull()
    expect(await db.reviews.count()).toBe(0)
  })

  it('重复刷过的题撤销后回到「已刷 1 次」，不是回到未刷', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    await repo.markSolved(p.id) // 第一次刷，solveCount=1
    const res = await repo.bulkMarkSolved([p.id])

    await undoRepo.undo(opIdOf(res))

    const restored = await repo.getById(p.id)
    expect(restored?.status).toBe('solved')
    expect(restored?.solveCount).toBe(1)
    expect(await db.reviews.count()).toBe(1) // 第一次的流水还在
  })

  it('同一 opId 只能撤销一次', async () => {
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    const res = await repo.bulkMarkSolved([p.id])
    const opId = opIdOf(res)

    await undoRepo.undo(opId)

    await expect(undoRepo.undo(opId)).rejects.toThrow('撤销已过期')
  })

  it('过期快照拒绝撤销', async () => {
    const token = registerUndo('测试', async () => 1, 0)
    await expect(undoRepo.undo(token.opId)).rejects.toThrow('撤销已过期')
  })
})

describe('批量打标签与加入题单的撤销', () => {
  it('打标签撤销后 tagIds 回到原值', async () => {
    const keep = await tags.create('数组', '#0ea5e9')
    const added = await tags.create('DP', '#6366f1')
    const p = await repo.create({ title: 'A', difficulty: 'easy', tagIds: [keep.id] })

    const res = await repo.bulkAddTags([p.id], [added.id])
    expect((await repo.getById(p.id))?.tagIds).toEqual([keep.id, added.id])

    await undoRepo.undo(opIdOf(res))
    expect((await repo.getById(p.id))?.tagIds).toEqual([keep.id])
  })

  it('加入题单撤销后关联被移除，题目本身还在', async () => {
    const list = await lists.create({ name: 'DP 30 题' })
    const p = await repo.create({ title: 'A', difficulty: 'easy' })

    const res = await repo.bulkAddToList([p.id], list.id)
    expect(res.affected).toBe(1)
    expect(await lists.problemIdsOf(list.id)).toEqual([p.id])

    await undoRepo.undo(opIdOf(res))
    expect(await lists.problemIdsOf(list.id)).toEqual([])
    expect(await repo.getById(p.id)).not.toBeNull()
  })
})

describe('批量删除的撤销', () => {
  it('整行还原题目、复习流水与题单关联', async () => {
    const list = await lists.create({ name: 'DP' })
    const p = await repo.create({ title: '删除后能还原', difficulty: 'hard', approach: '记忆化' })
    await repo.markSolved(p.id)
    await repo.bulkAddToList([p.id], list.id)

    const res = await repo.bulkRemove([p.id])
    expect(res.affected).toBe(1)
    expect(await repo.getById(p.id)).toBeNull()
    expect(await db.reviews.count()).toBe(0)
    expect(await lists.problemIdsOf(list.id)).toEqual([])

    const undone = await undoRepo.undo(opIdOf(res))
    expect(undone.affected).toBe(1)

    const restored = await repo.getById(p.id)
    expect(restored?.title).toBe('删除后能还原')
    expect(restored?.approach).toBe('记忆化')
    expect(restored?.status).toBe('solved')
    expect(await db.reviews.count()).toBe(1)
    expect(await lists.problemIdsOf(list.id)).toEqual([p.id])
  })
})
