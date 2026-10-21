import { db } from '@/db'
import type { OverviewStats, StatsRepository } from '@/types/repository'
import { todayStart, tomorrowStart } from '@/utils/date'
import { dexieListRepository } from './listRepository'

/**
 * 概览指标（Dexie）。
 *
 * 三个口径与后端 StatsService 严格同构，M8 对拍就是比这三个数：
 * - dueCount：已刷且 nextReviewAt <= now（不是「今天到期」，理由见 reviewRepository）
 * - solvedToday：今日 MARK_SOLVED 流水里**不同题目**的数量（同一题刷两次算一道）
 * - reviewedToday：今日 REVIEW 流水条数（同一题复习两次算两次，衡量的是工作量）
 */
export const dexieStatsRepository: StatsRepository = {
  async overview(): Promise<OverviewStats> {
    const now = Date.now()
    const start = todayStart(now)
    const end = tomorrowStart(now)

    const [problems, reviews, listProgress] = await Promise.all([
      db.problems.toArray(),
      db.reviews.where('reviewedAt').between(start, end, true, false).toArray(),
      dexieListRepository.list(),
    ])

    const alive = problems.filter((p) => (p.archived ?? 0) === 0)
    const dueCount = alive.filter(
      (p) => p.status === 'solved' && p.nextReviewAt != null && p.nextReviewAt <= now,
    ).length

    const solvedIds = new Set(
      reviews.filter((r) => r.kind === 'MARK_SOLVED').map((r) => r.problemId),
    )
    const reviewedToday = reviews.filter((r) => r.kind === 'REVIEW').length

    return {
      dueCount,
      solvedToday: solvedIds.size,
      reviewedToday,
      totalProblems: alive.length,
      solvedProblems: alive.filter((p) => p.status === 'solved').length,
      listProgress,
    }
  },
}
