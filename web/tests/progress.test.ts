import { describe, expect, it } from 'vitest'
import { PROGRESS_LABEL, progressBucket, toPercent } from '@/utils/progress'

describe('进度百分比', () => {
  it('常规取整', () => {
    expect(toPercent(12, 30)).toBe(40)
    expect(toPercent(1, 3)).toBe(33)
    expect(toPercent(2, 3)).toBe(67)
    expect(toPercent(30, 30)).toBe(100)
  })

  it('空题单返 0，不做除零也不返 NaN', () => {
    expect(toPercent(0, 0)).toBe(0)
    expect(toPercent(5, 0)).toBe(0)
    expect(toPercent(0, -1)).toBe(0)
    expect(Number.isNaN(toPercent(0, 0))).toBe(false)
  })

  it('脏数据夹到 0..100：已刷数不会超过总数', () => {
    expect(toPercent(40, 30)).toBe(100)
    expect(toPercent(-3, 30)).toBe(0)
    expect(toPercent(Number.NaN, 30)).toBe(0)
    expect(toPercent(10, Number.POSITIVE_INFINITY)).toBe(0)
  })
})

describe('进度分档', () => {
  it('空 / 未开始 / 进行中 / 已完成', () => {
    expect(progressBucket(0, 0)).toBe('empty')
    expect(progressBucket(0, 10)).toBe('todo')
    expect(progressBucket(3, 10)).toBe('active')
    expect(progressBucket(10, 10)).toBe('done')
    // 数据异常时按已完成处理，不会出现 110% 这种分档
    expect(progressBucket(12, 10)).toBe('done')
  })

  it('四档都有中文标签', () => {
    expect(PROGRESS_LABEL.empty).toBe('空题单')
    expect(PROGRESS_LABEL.done).toBe('已完成')
  })
})
