import type { Rating } from '@/types/enums'
import { addDays, tomorrowStart } from '@/utils/date'

/**
 * 间隔重复算法（前端同构实现）。
 *
 * 后端 ReviewScheduler 是权威实现，这里是逐行翻译版本：
 * - M1-M7 阶段用 Dexie 跑界面时由它计算
 * - 切到 http 后降级为「乐观预演」，最终以服务端响应整体覆盖
 *
 * 规则：1/3/7/15 天四阶；good 升阶、hard 退阶、again 归零（次日再来）；
 * 逾期一律从 now 起算，不补算欠账。
 */
export const INTERVALS = [1, 3, 7, 15] as const
export const MAX_STAGE = 4

export function intervalOf(stage: number): number {
  const idx = Math.min(Math.max(stage, 0), INTERVALS.length - 1)
  return INTERVALS[idx] ?? 1
}

export interface ScheduleResult {
  stage: number
  intervalDays: number
  nextReviewAt: number
}

export function nextStage(currentStage: number, rating: Rating): number {
  switch (rating) {
    case 'good':
      return Math.min(MAX_STAGE, currentStage + 1)
    case 'hard':
      return Math.max(0, currentStage - 1)
    case 'again':
      return 0
  }
}

export function schedule(currentStage: number, rating: Rating, now: number = Date.now()): ScheduleResult {
  const stage = nextStage(currentStage, rating)
  if (rating === 'again') {
    // 「明天再来」：落在次日零点，而不是 now + 24h
    return { stage, intervalDays: 1, nextReviewAt: tomorrowStart(now) }
  }
  const intervalDays = intervalOf(stage)
  return { stage, intervalDays, nextReviewAt: addDays(now, intervalDays) }
}

/** 首次标记已刷：进入 stage 0，1 天后复习 */
export function scheduleOnFirstSolve(now: number = Date.now()): ScheduleResult {
  return { stage: 0, intervalDays: 1, nextReviewAt: addDays(now, 1) }
}
