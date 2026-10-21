import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { dexieProblemRepository as repo } from '@/repositories/dexie/problemRepository'
import { dexieListRepository as lists } from '@/repositories/dexie/listRepository'

beforeEach(async () => {
  await db.open()
  await Promise.all([db.problems.clear(), db.reviews.clear(), db.listItems.clear(), db.problemLists.clear()])
})

describe('题单 CRUD', () => {
  it('创建后出现在列表里，进度为 0/0', async () => {
    const created = await lists.create({ name: '  动态规划  ', description: '  30 题  ' })
    expect(created.name).toBe('动态规划')
    expect(created.description).toBe('30 题')

    const all = await lists.list()
    expect(all).toHaveLength(1)
    expect(all[0]).toMatchObject({ name: '动态规划', total: 0, solved: 0 })
  })

  it('空名与超长名被拒绝', async () => {
    await expect(lists.create({ name: '   ' })).rejects.toThrow('题单名不能为空')
    await expect(lists.create({ name: 'x'.repeat(65) })).rejects.toThrow('题单名不能超过 64 个字符')
  })

  it('改名与改说明', async () => {
    const l = await lists.create({ name: '旧名' })
    const updated = await lists.update(l.id, { name: '新名', description: '说明' })
    expect(updated.name).toBe('新名')
    expect(updated.description).toBe('说明')
  })

  it('删除题单连带清掉关联行，题目不受影响', async () => {
    const l = await lists.create({ name: 'DP' })
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    await lists.addProblems(l.id, [p.id])

    await lists.remove(l.id)

    expect(await db.problemLists.count()).toBe(0)
    expect(await db.listItems.count()).toBe(0)
    expect(await repo.getById(p.id)).not.toBeNull()
  })
})

describe('题单进度', () => {
  it('只统计存活题目，已删题不算进分母', async () => {
    const l = await lists.create({ name: 'DP' })
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'medium' })
    await lists.addProblems(l.id, [a.id, b.id])
    await repo.markSolved(a.id)
    await repo.remove(b.id)

    const row = (await lists.list())[0]
    expect(row?.total).toBe(1)
    expect(row?.solved).toBe(1)
  })

  it('重复加入同一批题目是幂等的', async () => {
    const l = await lists.create({ name: 'DP' })
    const p = await repo.create({ title: 'A', difficulty: 'easy' })

    const first = await lists.addProblems(l.id, [p.id])
    const second = await lists.addProblems(l.id, [p.id])

    expect(first.affected).toBe(1)
    expect(second.affected).toBe(0)
    expect(await lists.problemIdsOf(l.id)).toEqual([p.id])
  })

  it('按加入顺序返回题目 id', async () => {
    const l = await lists.create({ name: 'DP' })
    const a = await repo.create({ title: 'A', difficulty: 'easy' })
    const b = await repo.create({ title: 'B', difficulty: 'easy' })
    const c = await repo.create({ title: 'C', difficulty: 'easy' })

    await lists.addProblems(l.id, [c.id, a.id, b.id])

    expect(await lists.problemIdsOf(l.id)).toEqual([c.id, a.id, b.id])
  })

  it('移出题单后进度跟着变', async () => {
    const l = await lists.create({ name: 'DP' })
    const p = await repo.create({ title: 'A', difficulty: 'easy' })
    await lists.addProblems(l.id, [p.id])

    await lists.removeProblem(l.id, p.id)

    expect((await lists.list())[0]?.total).toBe(0)
    await expect(lists.removeProblem(l.id, p.id)).rejects.toThrow('该题不在此题单内')
  })
})
