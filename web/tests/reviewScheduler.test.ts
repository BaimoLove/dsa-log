import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { INTERVALS, intervalOf, nextStage, schedule } from '@/services/reviewScheduler'
import { todayStart } from '@/utils/date'

/**
 * 复习算法前端同构版本的单元测试。
 * 后端 ReviewScheduler 必须有同样的用例表，两边结果不一致就是 bug。
 */
const NOW = new Date('2026-10-21T14:30:00+08:00').getTime()
const DAY = 24 * 60 * 60 * 1000

describe('intervalOf', () => {
  it('四阶间隔：1/3/7/15 天', () => {
    expect(INTERVALS).toEqual([1, 3, 7, 15])
    expect([0, 1, 2, 3].map(intervalOf)).toEqual([1, 3, 7, 15])
  })

  it('阶段 4（毕业）复用 15 天', () => {
    expect(intervalOf(4)).toBe(15)
    expect(intervalOf(99)).toBe(15)
  })
})

describe('nextStage', () => {
  it('good 升阶并封顶 4', () => {
    expect(nextStage(0, 'good')).toBe(1)
    expect(nextStage(3, 'good')).toBe(4)
    expect(nextStage(4, 'good')).toBe(4)
  })

  it('hard 退阶但不低于 0', () => {
    expect(nextStage(3, 'hard')).toBe(2)
    expect(nextStage(0, 'hard')).toBe(0)
  })

  it('again 归零', () => {
    expect(nextStage(4, 'again')).toBe(0)
  })
})

describe('schedule', () => {
  it('good 从 now 起算新阶段间隔', () => {
    const r = schedule(0, 'good', NOW)
    expect(r.stage).toBe(1)
    expect(r.intervalDays).toBe(3)
    expect(r.nextReviewAt).toBe(NOW + 3 * DAY)
  })

  it('again 落在次日零点，而不是 now + 24h', () => {
    const r = schedule(2, 'again', NOW)
    expect(r.stage).toBe(0)
    expect(r.nextReviewAt).toBe(todayStart(NOW) + DAY)
    expect(r.nextReviewAt).toBeGreaterThan(NOW)
  })

  it('逾期不影响计算：基准始终是 now', () => {
    const overdueNow = NOW + 10 * DAY
    const r = schedule(1, 'good', overdueNow)
    expect(r.nextReviewAt).toBe(overdueNow + 7 * DAY)
  })
})
