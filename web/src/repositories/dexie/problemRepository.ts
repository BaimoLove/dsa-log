import { db } from '@/db'
import { AppError } from '@/types/common'
import type { ReviewKind, SortOption } from '@/types/enums'
import { DIFFICULTY_WEIGHT } from '@/types/enums'
import type { Problem, ReviewRecord, TagBrief } from '@/types/models'
import type {
  BatchResult,
  CreateProblemInput,
  MarkSolvedResult,
  Paginated,
  ProblemQuery,
  ProblemRepository,
  RandomPickResult,
  UpdateProblemInput,
} from '@/types/repository'
import { scheduleOnFirstSolve } from '@/services/reviewScheduler'
import { pickRandomProblem } from '@/services/randomPicker'
import { newId, uniqueIds } from '@/utils/id'
import { dexieListRepository } from './listRepository'

/**
 * Dexie 版题目仓储。
 *
 * 两个约定：
 * 1. 表里存的 `tags` 恒为空数组，标签详情只在读取时按 `tagIds` 现拼——
 *    否则标签改名后旧题上的名字会一直停在旧值。
 * 2. 组合筛选（关键词 × 难度 × 状态 × 标签 × 题单）走内存过滤；
 *    索引只服务单维查询与待复习队列，避免复合索引爆炸。
 */
const DEFAULT_SIZE = 20
const MAX_SIZE = 100

/** 表行：tags 不落库 */
function toRow(p: Problem): Problem {
  return { ...p, tagIds: uniqueIds(p.tagIds ?? []), tags: [] }
}

async function tagMap(): Promise<Map<string, TagBrief>> {
  const tags = await db.tags.toArray()
  return new Map(tags.map((t) => [t.id, { id: t.id, name: t.name, color: t.color }]))
}

function hydrate(row: Problem, map: Map<string, TagBrief>): Problem {
  const tagIds = row.tagIds ?? []
  return {
    ...row,
    tagIds,
    tags: tagIds.map((id) => map.get(id)).filter((t): t is TagBrief => Boolean(t)),
  }
}

async function hydrateMany(rows: Problem[]): Promise<Problem[]> {
  if (rows.length === 0) return []
  const map = await tagMap()
  return rows.map((r) => hydrate(r, map))
}

async function requireProblem(id: string): Promise<Problem> {
  const row = await db.problems.get(id)
  if (!row || row.archived === 1) throw new AppError('NOT_FOUND', '题目不存在或已删除')
  return row
}

function matchesKeyword(p: Problem, keyword: string): boolean {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return true
  return [p.title, p.approach, p.note, p.source].some((f) => (f ?? '').toLowerCase().includes(kw))
}

function sortProblems(items: Problem[], sort: SortOption = 'updatedAt_desc'): Problem[] {
  const byNumber = (a: number | null, b: number | null) => (a ?? 0) - (b ?? 0)
  const sorted = [...items]
  switch (sort) {
    case 'updatedAt_asc':
      return sorted.sort((a, b) => byNumber(a.updatedAt, b.updatedAt))
    case 'createdAt_desc':
      return sorted.sort((a, b) => byNumber(b.createdAt, a.createdAt))
    case 'createdAt_asc':
      return sorted.sort((a, b) => byNumber(a.createdAt, b.createdAt))
    case 'difficulty_asc':
      return sorted.sort((a, b) => DIFFICULTY_WEIGHT[a.difficulty] - DIFFICULTY_WEIGHT[b.difficulty])
    case 'difficulty_desc':
      return sorted.sort((a, b) => DIFFICULTY_WEIGHT[b.difficulty] - DIFFICULTY_WEIGHT[a.difficulty])
    case 'nextReviewAt_asc':
      return sorted.sort((a, b) => byNumber(a.nextReviewAt, b.nextReviewAt))
    case 'solvedAt_desc':
      return sorted.sort((a, b) => byNumber(b.solvedAt, a.solvedAt))
    case 'updatedAt_desc':
    default:
      return sorted.sort((a, b) => byNumber(b.updatedAt, a.updatedAt))
  }
}

function addReview(
  problemId: string,
  kind: ReviewKind,
  at: number,
  extra: Partial<Pick<ReviewRecord, 'rating' | 'fromStage' | 'toStage' | 'intervalDays' | 'nextReviewAt'>> = {},
): ReviewRecord {
  return {
    id: newId(),
    problemId,
    kind,
    rating: extra.rating ?? null,
    fromStage: extra.fromStage ?? null,
    toStage: extra.toStage ?? null,
    intervalDays: extra.intervalDays ?? null,
    nextReviewAt: extra.nextReviewAt ?? null,
    reviewedAt: at,
    note: '',
  }
}

async function getByIdImpl(id: string): Promise<Problem | null> {
  const row = await db.problems.get(id)
  if (!row || row.archived === 1) return null
  return hydrate(row, await tagMap())
}

/** 删除即级联：reviews 与 listItems 一并清掉，不留孤儿行 */
async function removeImpl(id: string): Promise<void> {
  await db.transaction('rw', db.problems, db.reviews, db.listItems, async () => {
    await db.reviews.where('problemId').equals(id).delete()
    await db.listItems.where('problemId').equals(id).delete()
    await db.problems.delete(id)
  })
}

export const dexieProblemRepository: ProblemRepository = {
  async list(query: ProblemQuery): Promise<Paginated<Problem>> {
    const page = Math.max(1, query.page ?? 1)
    const size = Math.min(MAX_SIZE, Math.max(1, query.size ?? DEFAULT_SIZE))
    const archived = query.archived ?? 0

    let rows = await db.problems.toArray()

    if (query.listId) {
      const links = await db.listItems.where('listId').equals(query.listId).toArray()
      const allowed = new Set(links.map((l) => l.problemId))
      rows = rows.filter((r) => allowed.has(r.id))
    }

    rows = rows.filter((r) => (r.archived ?? 0) === archived)
    if (query.keyword) rows = rows.filter((r) => matchesKeyword(r, query.keyword as string))
    if (query.difficulties?.length) {
      const set = new Set(query.difficulties)
      rows = rows.filter((r) => set.has(r.difficulty))
    }
    if (query.statuses?.length) {
      const set = new Set(query.statuses)
      rows = rows.filter((r) => set.has(r.status))
    }
    if (query.tagIds?.length) {
      // 多标签是 AND：每个选中的标签都必须命中
      const wanted = uniqueIds(query.tagIds)
      rows = rows.filter((r) => wanted.every((id) => (r.tagIds ?? []).includes(id)))
    }

    const sorted = sortProblems(rows, query.sort)
    const total = sorted.length
    const totalPages = Math.max(1, Math.ceil(total / size))
    const start = (page - 1) * size
    const items = await hydrateMany(sorted.slice(start, start + size))

    return { items, total, page, size, totalPages }
  },

  getById: getByIdImpl,

  async create(input: CreateProblemInput): Promise<Problem> {
    const title = input.title.trim()
    if (!title) throw new AppError('VALIDATION_FAILED', '标题不能为空')
    const now = Date.now()
    const row: Problem = {
      id: newId(),
      title,
      difficulty: input.difficulty,
      status: 'todo',
      approach: input.approach ?? '',
      note: input.note ?? '',
      source: input.source ?? '',
      url: input.url ?? '',
      tagIds: uniqueIds(input.tagIds ?? []),
      tags: [],
      solvedAt: null,
      lastSolvedAt: null,
      solveCount: 0,
      reviewStage: 0,
      nextReviewAt: null,
      lastReviewedAt: null,
      archived: 0,
      createdAt: now,
      updatedAt: now,
    }
    await db.problems.add(toRow(row))
    return hydrate(row, await tagMap())
  },

  async update(id: string, patch: UpdateProblemInput): Promise<Problem> {
    await requireProblem(id)
    const changes: Partial<Problem> = { updatedAt: Date.now() }
    if (patch.title !== undefined) {
      const title = patch.title.trim()
      if (!title) throw new AppError('VALIDATION_FAILED', '标题不能为空')
      changes.title = title
    }
    if (patch.difficulty !== undefined) changes.difficulty = patch.difficulty
    if (patch.approach !== undefined) changes.approach = patch.approach
    if (patch.note !== undefined) changes.note = patch.note
    if (patch.source !== undefined) changes.source = patch.source
    if (patch.url !== undefined) changes.url = patch.url
    if (patch.archived !== undefined) changes.archived = patch.archived
    // 三态：不传=不改，[]=清空
    if (patch.tagIds !== undefined) changes.tagIds = uniqueIds(patch.tagIds)

    await db.problems.update(id, changes as never)
    return (await getByIdImpl(id)) as Problem
  },

  remove: removeImpl,

  async markSolved(id: string): Promise<MarkSolvedResult> {
    // 注意：事务里绝不碰 tags 表（未纳入 scope 会直接抛 NotFoundError），
    // 标签拼装一律放到事务外。
    const result = await db.transaction('rw', db.problems, db.reviews, async () => {
      const row = await requireProblem(id)
      const now = Date.now()
      const firstSolve = row.solvedAt == null
      // 入池判据是 nextReviewAt 而不是 solvedAt：取消已刷会清排期但保留 solvedAt
      // 作为历史，沿用「首次」判据会让取消后再标记的题永远进不了复习池。
      // 首次进池；已在池内的重复刷不改排期，排期只由复习评分推进。
      const inPool = row.nextReviewAt != null
      const sched = inPool ? null : scheduleOnFirstSolve(now)

      const next: Problem = {
        ...row,
        status: 'solved',
        solvedAt: row.solvedAt ?? now,
        lastSolvedAt: now,
        solveCount: (row.solveCount ?? 0) + 1,
        reviewStage: sched ? sched.stage : row.reviewStage,
        nextReviewAt: sched ? sched.nextReviewAt : row.nextReviewAt,
        updatedAt: now,
      }
      await db.problems.put(toRow(next))
      await db.reviews.add(
        addReview(id, 'MARK_SOLVED', now, {
          fromStage: row.reviewStage,
          toStage: next.reviewStage,
          intervalDays: sched?.intervalDays ?? null,
          nextReviewAt: next.nextReviewAt,
        }),
      )
      return { next, firstSolve }
    })

    return {
      problem: hydrate(result.next, await tagMap()),
      firstSolve: result.firstSolve,
      nextReviewAt: result.next.nextReviewAt,
    }
  },

  async markUnsolved(id: string): Promise<Problem> {
    const next = await db.transaction('rw', db.problems, db.reviews, async () => {
      const row = await requireProblem(id)
      const now = Date.now()
      if (row.status === 'todo') return row

      const updated: Problem = {
        ...row,
        status: 'todo',
        // solvedAt / solveCount 是历史事实，不抹掉
        nextReviewAt: null,
        reviewStage: 0,
        updatedAt: now,
      }
      await db.problems.put(toRow(updated))
      await db.reviews.add(addReview(id, 'MARK_UNSOLVED', now, { fromStage: row.reviewStage, toStage: 0 }))
      return updated
    })
    return hydrate(next, await tagMap())
  },

  async bulkMarkSolved(ids: string[]): Promise<BatchResult> {
    const targets = uniqueIds(ids)
    if (!targets.length) return { affected: 0 }

    let affected = 0
    await db.transaction('rw', db.problems, db.reviews, async () => {
      for (const id of targets) {
        const row = await db.problems.get(id)
        if (!row || row.archived === 1) continue
        const now = Date.now()
        const inPool = row.nextReviewAt != null
        const sched = inPool ? null : scheduleOnFirstSolve(now)
        const next: Problem = {
          ...row,
          status: 'solved',
          solvedAt: row.solvedAt ?? now,
          lastSolvedAt: now,
          solveCount: (row.solveCount ?? 0) + 1,
          reviewStage: sched ? sched.stage : row.reviewStage,
          nextReviewAt: sched ? sched.nextReviewAt : row.nextReviewAt,
          updatedAt: now,
        }
        await db.problems.put(toRow(next))
        await db.reviews.add(
          addReview(id, 'MARK_SOLVED', now, {
            fromStage: row.reviewStage,
            toStage: next.reviewStage,
            intervalDays: sched?.intervalDays ?? null,
            nextReviewAt: next.nextReviewAt,
          }),
        )
        affected += 1
      }
    })
    return { affected }
  },

  /**
   * 批量取消已刷：批量已刷的相反操作。
   * 语义与单条 markUnsolved 一致——回到 todo、清排期，但 solvedAt / solveCount 是历史事实，保留。
   */
  async bulkMarkUnsolved(ids: string[]): Promise<BatchResult> {
    const targets = uniqueIds(ids)
    if (!targets.length) return { affected: 0 }

    let affected = 0
    await db.transaction('rw', db.problems, db.reviews, async () => {
      for (const id of targets) {
        const row = await db.problems.get(id)
        if (!row || row.archived === 1 || row.status === 'todo') continue
        const now = Date.now()
        const next: Problem = {
          ...row,
          status: 'todo',
          nextReviewAt: null,
          reviewStage: 0,
          updatedAt: now,
        }
        await db.problems.put(toRow(next))
        await db.reviews.add(
          addReview(id, 'MARK_UNSOLVED', now, { fromStage: row.reviewStage, toStage: 0 }),
        )
        affected += 1
      }
    })
    return { affected }
  },

  async bulkAddTags(ids: string[], tagIds: string[]): Promise<BatchResult> {
    const targets = uniqueIds(ids)
    const tags = uniqueIds(tagIds)
    if (!targets.length || !tags.length) return { affected: 0 }

    let affected = 0
    await db.transaction('rw', db.problems, async () => {
      for (const id of targets) {
        const row = await db.problems.get(id)
        if (!row || row.archived === 1) continue
        const oldTagIds = row.tagIds ?? []
        if (tags.every((t) => oldTagIds.includes(t))) continue // 已经是这个标签，不产生变更
        await db.problems.update(id, {
          tagIds: uniqueIds([...oldTagIds, ...tags]),
          updatedAt: Date.now(),
        })
        affected += 1
      }
    })
    return { affected }
  },

  /** 加入题单与题单页的「加入题目」是同一件事，统一由题单仓储实现 */
  bulkAddToList(ids: string[], listId: string): Promise<BatchResult> {
    return dexieListRepository.addProblems(listId, ids)
  },

  async bulkRemove(ids: string[]): Promise<BatchResult> {
    const targets = uniqueIds(ids)
    if (!targets.length) return { affected: 0 }

    let affected = 0
    await db.transaction('rw', db.problems, db.reviews, db.listItems, async () => {
      // 先按实际存在的行筛一遍，affected 才等于真正删掉的条数
      const existing: string[] = []
      for (const id of targets) {
        if (await db.problems.get(id)) existing.push(id)
      }
      if (!existing.length) return
      await db.reviews.where('problemId').anyOf(existing).delete()
      await db.listItems.where('problemId').anyOf(existing).delete()
      await db.problems.bulkDelete(existing)
      affected = existing.length
    })
    return { affected }
  },

  async random(excludeIds: string[]): Promise<RandomPickResult | null> {
    const rows = await db.problems.toArray()
    const outcome = pickRandomProblem(rows, excludeIds)
    if (!outcome) return null
    const map = await tagMap()
    return {
      problem: hydrate(outcome.item, map),
      pool: outcome.pool,
      reset: outcome.reset,
      candidateCount: outcome.candidateCount,
    }
  },

  async upsertMany(items: Problem[]): Promise<{ created: number; updated: number; skipped: number }> {
    let created = 0
    let updated = 0
    let skipped = 0
    const now = Date.now()
    await db.transaction('rw', db.problems, db.reviews, db.tags, db.problemLists, db.listItems, async () => {
      for (const item of items) {
        const existing = await db.problems.get(item.id)
        const row = toRow({ ...item, tags: [], updatedAt: item.updatedAt ?? now })
        if (!existing) {
          await db.problems.add(row)
          created += 1
        } else if ((existing.updatedAt ?? 0) >= (row.updatedAt ?? 0)) {
          skipped += 1
        } else {
          await db.problems.put(row)
          updated += 1
        }
      }
    })
    return { created, updated, skipped }
  },
}
