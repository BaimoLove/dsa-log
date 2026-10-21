import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { problemRepo } from '@/repositories'
import { dexieListRepository as listRepo } from '@/repositories/dexie/listRepository'
import { toPercent } from '@/utils/progress'

/**
 * M5 的验收点：看板上的 `12/30` 必须等于题单内 `?status=solved` 的 total。
 * 两者走的是不同的查询路径（一个现算关联表、一个走 listId 过滤），
 * 所以这条用例是真在防「两边口径漂移」。
 */
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

async function seedList(total: number, solved: number, name = '动态规划 30 题') {
  const list = await listRepo.create({ name })
  const created = []
  for (let i = 0; i < total; i += 1) {
    created.push(await problemRepo.create({ title: `${name} 第 ${i + 1} 题`, difficulty: 'easy' }))
  }
  await listRepo.addProblems(list.id, created.map((p) => p.id))
  for (const p of created.slice(0, solved)) await problemRepo.markSolved(p.id)
  return { list, problems: created }
}

describe('题单进度与题单内筛选同口径', () => {
  it('30 道刷 12 道：看板 12/30，题单内 solved=12、todo=18', async () => {
    const { list } = await seedList(30, 12)

    const [progress] = await listRepo.list()
    expect(progress.total).toBe(30)
    expect(progress.solved).toBe(12)
    expect(toPercent(progress.solved, progress.total)).toBe(40)

    const solved = await problemRepo.list({ listId: list.id, statuses: ['solved'], page: 1, size: 100 })
    const todo = await problemRepo.list({ listId: list.id, statuses: ['todo'], page: 1, size: 100 })
    const all = await problemRepo.list({ listId: list.id, page: 1, size: 100 })

    expect(solved.total).toBe(progress.solved)
    expect(todo.total).toBe(progress.total - progress.solved)
    expect(all.total).toBe(progress.total)
  })

  it('取消已刷后立刻回落，不靠缓存', async () => {
    const { list, problems } = await seedList(5, 2)
    await problemRepo.markUnsolved(problems[0].id)

    const [progress] = await listRepo.list()
    expect(progress.solved).toBe(1)
    expect(progress.total).toBe(5)

    const solved = await problemRepo.list({ listId: list.id, statuses: ['solved'], page: 1, size: 100 })
    expect(solved.total).toBe(1)
  })

  it('删掉一道已刷题：分母分子一起减，进度不会虚高', async () => {
    const { list, problems } = await seedList(4, 2)
    await problemRepo.remove(problems[0].id)

    const [progress] = await listRepo.list()
    expect(progress.total).toBe(3)
    expect(progress.solved).toBe(1)
    expect(toPercent(progress.solved, progress.total)).toBe(33)

    const all = await problemRepo.list({ listId: list.id, page: 1, size: 100 })
    expect(all.total).toBe(progress.total)
  })

  it('空题单的进度是 0/0，百分比不出现 NaN', async () => {
    await listRepo.create({ name: '空题单' })
    const [progress] = await listRepo.list()
    expect(progress.total).toBe(0)
    expect(progress.solved).toBe(0)
    expect(toPercent(progress.solved, progress.total)).toBe(0)
  })

  it('分页：每页 10 条时第 2 页与第 1 页无重复 id', async () => {
    const { list } = await seedList(25, 0)
    const page1 = await problemRepo.list({ listId: list.id, page: 1, size: 10 })
    const page2 = await problemRepo.list({ listId: list.id, page: 2, size: 10 })
    const page3 = await problemRepo.list({ listId: list.id, page: 3, size: 10 })

    expect(page1.items.length).toBe(10)
    expect(page2.items.length).toBe(10)
    expect(page3.items.length).toBe(5)
    expect(page1.totalPages).toBe(3)

    const all = new Set([...page1.items, ...page2.items, ...page3.items].map((p) => p.id))
    expect(all.size).toBe(25)
  })
})
