import { db } from '@/db'
import { AppError } from '@/types/common'
import type { ProblemList } from '@/types/models'
import type { BatchResult, ListProgress, ListRepository, ProblemListBasic } from '@/types/repository'
import { newId, uniqueIds } from '@/utils/id'

/**
 * 题单仓储（Dexie）。
 *
 * 两个要点：
 * 1. 进度用「关联表 × 存活题目」现算，不缓存计数——题目删了、标记已刷了都不会有残留数字。
 *    SQL 侧对应 PLAN 2.3 那条一条 SQL 出全部进度的写法（COUNT(p.id) 而非 COUNT(*)）。
 * 2. 题单删除连带清 listItems（逻辑上等于后端的事务内显式级联）；题目侧删除在
 *    problemRepository.remove 里已经级联清 listItems，两边都兜住。
 */

function toBasic(row: ProblemList): ProblemListBasic {
  return {
    id: row.id,
    name: row.name,
    description: row.description ?? '',
    color: row.color,
    sortOrder: row.sortOrder ?? 0,
  }
}

function assertName(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) throw new AppError('VALIDATION_FAILED', '题单名不能为空')
  if (trimmed.length > 64) throw new AppError('VALIDATION_FAILED', '题单名不能超过 64 个字符')
  return trimmed
}

export const dexieListRepository: ListRepository = {
  async list(): Promise<ListProgress[]> {
    const [lists, links, problems] = await Promise.all([
      db.problemLists.toArray(),
      db.listItems.toArray(),
      db.problems.toArray(),
    ])

    // 只统计存活题目，已删题不能算进分母
    const alive = new Map(problems.filter((p) => (p.archived ?? 0) === 0).map((p) => [p.id, p]))
    const counts = new Map<string, { total: number; solved: number }>()
    for (const link of links) {
      const p = alive.get(link.problemId)
      if (!p) continue
      const acc = counts.get(link.listId) ?? { total: 0, solved: 0 }
      acc.total += 1
      if (p.status === 'solved') acc.solved += 1
      counts.set(link.listId, acc)
    }

    return lists
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.createdAt - b.createdAt)
      .map((l) => ({ ...toBasic(l), ...(counts.get(l.id) ?? { total: 0, solved: 0 }) }))
  },

  async getById(id: string): Promise<ProblemListBasic | null> {
    const row = await db.problemLists.get(id)
    return row ? toBasic(row) : null
  },

  async create(input: { name: string; description?: string; color?: string }): Promise<ProblemListBasic> {
    const now = Date.now()
    const count = await db.problemLists.count()
    const row: ProblemList = {
      id: newId(),
      name: assertName(input.name),
      description: input.description?.trim() ?? '',
      color: input.color ?? '#6366f1',
      sortOrder: count,
      createdAt: now,
      updatedAt: now,
    }
    await db.problemLists.add(row)
    return toBasic(row)
  },

  async update(
    id: string,
    patch: { name?: string; description?: string; color?: string },
  ): Promise<ProblemListBasic> {
    const row = await db.problemLists.get(id)
    if (!row) throw new AppError('NOT_FOUND', '题单不存在')
    const changes: Partial<ProblemList> = { updatedAt: Date.now() }
    if (patch.name !== undefined) changes.name = assertName(patch.name)
    if (patch.description !== undefined) changes.description = patch.description.trim()
    if (patch.color !== undefined) changes.color = patch.color
    await db.problemLists.update(id, changes)
    return toBasic({ ...row, ...changes })
  },

  async remove(id: string): Promise<void> {
    await db.transaction('rw', db.problemLists, db.listItems, async () => {
      if (!(await db.problemLists.get(id))) throw new AppError('NOT_FOUND', '题单不存在')
      await db.listItems.where('listId').equals(id).delete()
      await db.problemLists.delete(id)
    })
  },

  async addProblems(listId: string, problemIds: string[]): Promise<BatchResult> {
    const targets = uniqueIds(problemIds)
    if (!targets.length) return { affected: 0 }
    const list = await db.problemLists.get(listId)
    if (!list) throw new AppError('NOT_FOUND', '题单不存在')

    let affected = 0
    await db.transaction('rw', db.listItems, async () => {
      const existing = new Set(
        (await db.listItems.where('listId').equals(listId).toArray()).map((l) => l.problemId),
      )
      const tail = await db.listItems.where('listId').equals(listId).count()
      const now = Date.now()
      let offset = 0
      for (const problemId of targets) {
        if (existing.has(problemId)) continue
        await db.listItems.put({ listId, problemId, sortOrder: tail + offset, addedAt: now })
        offset += 1
      }
      affected = offset
    })
    return { affected }
  },

  async removeProblem(listId: string, problemId: string): Promise<void> {
    await db.transaction('rw', db.listItems, async () => {
      const row = await db.listItems.get([listId, problemId])
      if (!row) throw new AppError('NOT_FOUND', '该题不在此题单内')
      await db.listItems.delete([listId, problemId])
    })
  },

  async problemIdsOf(listId: string): Promise<string[]> {
    const links = await db.listItems.where('listId').equals(listId).toArray()
    return links.sort((a, b) => a.sortOrder - b.sortOrder).map((l) => l.problemId)
  },
}
