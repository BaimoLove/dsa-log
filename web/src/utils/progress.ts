/**
 * 题单进度的统一口径。看板卡片、详情页进度环、顶部汇总都调这里，
 * 避免三个地方各写一份 `Math.round(a/b*100)` 然后边界行为不一致。
 */

/** 0..100 的整数百分比；total<=0 或非法值一律返 0，不做除零也绝不返回 NaN */
export function toPercent(solved: number, total: number): number {
  if (!Number.isFinite(solved) || !Number.isFinite(total) || total <= 0) return 0
  const hit = Math.min(Math.max(solved, 0), total)
  return Math.round((hit / total) * 100)
}

/** 卡片角标用的四态：空题单 / 未开始 / 进行中 / 已完成 */
export type ProgressBucket = 'empty' | 'todo' | 'active' | 'done'

export function progressBucket(solved: number, total: number): ProgressBucket {
  if (!Number.isFinite(total) || total <= 0) return 'empty'
  if (!Number.isFinite(solved) || solved <= 0) return 'todo'
  return solved >= total ? 'done' : 'active'
}

export const PROGRESS_LABEL: Record<ProgressBucket, string> = {
  empty: '空题单',
  todo: '未开始',
  active: '进行中',
  done: '已完成',
}
