import type { ProblemStatus } from '@/types/enums'

/**
 * 随机抽题：纯函数，不碰数据库，便于单测。
 *
 * 四级级联，保证「只要题库非空就一定抽得出来」：
 *   1) 未刷 ∩ 未抽   2) 未刷全部（本轮重置）
 *   3) 全库 ∩ 未抽   4) 全库全部（本轮重置）
 *   5) 题库为空 → null
 *
 * 不做难度加权：几百条数据下加权只会让行为不可预期。
 */
export type RandomPool = 'unsolved' | 'all'

export interface RandomCandidate {
  id: string
  status: ProblemStatus
  archived?: 0 | 1
}

export interface PickOutcome<T extends RandomCandidate> {
  item: T
  pool: RandomPool
  /** true 表示本轮候选已抽完，池子刚被重置 */
  reset: boolean
  candidateCount: number
}

export function pickRandomProblem<T extends RandomCandidate>(
  items: readonly T[],
  drawnIds: Iterable<string>,
  rand: () => number = Math.random,
): PickOutcome<T> | null {
  const alive = items.filter((it) => (it.archived ?? 0) === 0)
  if (alive.length === 0) return null

  const drawn = new Set(drawnIds)
  const unsolved = alive.filter((it) => it.status === 'todo')

  let candidates: T[] = []
  let pool: RandomPool = 'unsolved'
  let reset = false

  if (unsolved.length > 0) {
    candidates = unsolved.filter((it) => !drawn.has(it.id))
    if (candidates.length === 0) {
      candidates = unsolved
      reset = true
    }
  }

  if (candidates.length === 0) {
    pool = 'all'
    candidates = alive.filter((it) => !drawn.has(it.id))
    if (candidates.length === 0) {
      candidates = alive
      reset = true
    }
  }

  const index = pickIndex(candidates.length, rand)
  // candidates 非空由上文保证（alive.length > 0 且每级都回落到非空集合）
  const item = candidates[index] as T

  return {
    item,
    pool,
    reset,
    candidateCount: candidates.length,
  }
}

/** 均匀取下标；对注入的 rand 做 clamping，避免返回 1 时越界 */
export function pickIndex(length: number, rand: () => number = Math.random): number {
  if (length <= 0) return 0
  const raw = Math.floor(rand() * length)
  return Math.min(length - 1, Math.max(0, raw))
}
