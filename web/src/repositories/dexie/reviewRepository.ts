import { db } from '@/db'
import { schedule } from '@/services/reviewScheduler'
import { AppError } from '@/types/common'
import type { Rating } from '@/types/enums'
import type { Problem, ReviewRecord, TagBrief } from '@/types/models'
import type { DueReviewItem, DueReviewResult, RateResult, ReviewRepository } from '@/types/repository'
import { overdueDays } from '@/utils/date'
import { newId } from '@/utils/id'

/**
 * 复习仓储（Dexie）。
 *
 * 与后端 ReviewService 一一对应：
 * - due 的判据是「已刷 + 排期已到（nextReviewAt <= now）」，与 idx_due 的
 *   (deleted, status, next_review_at) 访问路径同构；**不是按自然日**——
 *   按自然日会让「早上到期」和「晚上到期」在下午时一个可见一个不可见。
 * - rate 只改排期，不改 status / solveCount；算法借 services/reviewScheduler，
 *   前端这份只服务 local 模式，切到 http 后以服务端响应为准。
 */
const DEFAULT_LIMIT = 100

async function tagMap(): Promise<Map<string, TagBrief>> {
  const tags = await db.tags.toArray()
  return new Map(tags.map((t) => [t.id, { id: t.id, name: t.name, color: t.color }]))
}

async function hydrate(row: Problem): Promise<Problem> {
  const map = await tagMap()
  const tagIds = row.tagIds ?? []
  return {
    ...row,
    tagIds,
    tags: tagIds.map((id) => map.get(id)).filter((t): t is TagBrief => Boolean(t)),
  }
}

export const dexieReviewRepository: ReviewRepository = {
  async due(limit?: number): Promise<DueReviewResult> {
    const now = Date.now()
    const cap = Math.max(1, limit ?? DEFAULT_LIMIT)

    const rows = await db.problems.where('status').equals('solved').toArray()
    const dueRows = rows
      .filter((p) => p.archived !== 1 && p.nextReviewAt != null && p.nextReviewAt <= now)
      .sort((a, b) => (a.nextReviewAt ?? 0) - (b.nextReviewAt ?? 0))

    const map = await tagMap()
    const items: DueReviewItem[] = dueRows.slice(0, cap).map((p) => {
      const dueAt = p.nextReviewAt as number
      const days = overdueDays(dueAt, now)
      const tagIds = p.tagIds ?? []
      return {
        problem: {
          ...p,
          tagIds,
          tags: tagIds.map((id) => map.get(id)).filter((t): t is TagBrief => Boolean(t)),
        },
        dueAt,
        overdueDays: Math.max(0, days),
        isOverdue: days > 0,
      }
    })

    // total 不受 limit 影响：前端要显示「今天还有 37 道」，而不是「最多 20 道」
    return { total: dueRows.length, limited: items.length, items }
  },

  async rate(problemId: string, rating: Rating): Promise<RateResult> {
    const result = await db.transaction('rw', db.problems, db.reviews, async () => {
      const row = await db.problems.get(problemId)
      if (!row || row.archived === 1) throw new AppError('NOT_FOUND', '题目不存在或已删除')
      if (row.nextReviewAt == null) {
        // 不在池里的题没有「阶段」可言，给它评分只会把排期凭空造出来
        throw new AppError('VALIDATION_FAILED', '这道题不在复习队列里')
      }

      const now = Date.now()
      const sched = schedule(row.reviewStage ?? 0, rating, now)
      const next: Problem = {
        ...row,
        reviewStage: sched.stage,
        nextReviewAt: sched.nextReviewAt,
        lastReviewedAt: now,
        updatedAt: now,
      }
      await db.problems.put({ ...next, tags: [] })

      const record: ReviewRecord = {
        id: newId(),
        problemId,
        kind: 'REVIEW',
        rating,
        fromStage: row.reviewStage ?? 0,
        toStage: sched.stage,
        intervalDays: sched.intervalDays,
        nextReviewAt: sched.nextReviewAt,
        reviewedAt: now,
        note: '',
      }
      await db.reviews.add(record)

      return { next, record }
    })

    return {
      problem: await hydrate(result.next),
      fromStage: result.record.fromStage ?? 0,
      toStage: result.record.toStage ?? 0,
      intervalDays: result.record.intervalDays ?? 1,
      nextReviewAt: result.record.nextReviewAt as number,
    }
  },

  async history(problemId: string): Promise<ReviewRecord[]> {
    const rows = await db.reviews.where('problemId').equals(problemId).toArray()
    return rows.sort((a, b) => b.reviewedAt - a.reviewedAt)
  },
}
