import { describe, expect, it } from 'vitest'
import { pickIndex, pickRandomProblem, type RandomCandidate } from '@/services/randomPicker'

const c = (id: string, status: 'todo' | 'solved' = 'todo', archived: 0 | 1 = 0): RandomCandidate => ({
  id,
  status,
  archived,
})

/** 固定序列的 rand，便于断言 */
const seq = (...values: number[]) => {
  let i = 0
  return () => values[i++ % values.length]
}

describe('pickIndex', () => {
  it('rand=0 取第一个，rand 趋近 1 取最后一个', () => {
    expect(pickIndex(5, () => 0)).toBe(0)
    expect(pickIndex(5, () => 0.999999)).toBe(4)
  })

  it('注入 rand 返回 1 时不越界', () => {
    expect(pickIndex(3, () => 1)).toBe(2)
  })

  it('长度为 0 时返回 0，不产生 NaN', () => {
    expect(pickIndex(0, () => 0.5)).toBe(0)
  })
})

describe('pickRandomProblem', () => {
  it('题库为空返回 null', () => {
    expect(pickRandomProblem([], [])).toBeNull()
  })

  it('全部归档视为空库', () => {
    expect(pickRandomProblem([c('a', 'todo', 1)], [])).toBeNull()
  })

  it('单元素池恒返回该元素', () => {
    const out = pickRandomProblem([c('only')], [], () => 0.9)
    expect(out?.item.id).toBe('only')
    expect(out?.pool).toBe('unsolved')
    expect(out?.reset).toBe(false)
  })

  it('默认从未刷里抽，已刷不参与', () => {
    const items = [c('s1', 'solved'), c('t1', 'todo'), c('s2', 'solved')]
    for (let i = 0; i < 20; i += 1) {
      expect(pickRandomProblem(items, [])?.item.id).toBe('t1')
    }
  })

  it('已抽过的不再返回', () => {
    const items = [c('t1'), c('t2'), c('t3')]
    const out = pickRandomProblem(items, ['t1', 't2'])
    expect(out?.item.id).toBe('t3')
    expect(out?.candidateCount).toBe(1)
  })

  it('未刷抽完时重置本轮记录，仍返回未刷题', () => {
    const items = [c('t1'), c('t2'), c('s1', 'solved')]
    const out = pickRandomProblem(items, ['t1', 't2'])
    expect(out?.reset).toBe(true)
    expect(out?.pool).toBe('unsolved')
    expect(['t1', 't2']).toContain(out?.item.id)
  })

  it('没有未刷题时回落到全库', () => {
    const items = [c('s1', 'solved'), c('s2', 'solved')]
    const out = pickRandomProblem(items, ['s1'])
    expect(out?.pool).toBe('all')
    expect(out?.item.id).toBe('s2')
    expect(out?.reset).toBe(false)
  })

  it('全库也抽完时重置，返回全库任一题', () => {
    const items = [c('s1', 'solved'), c('s2', 'solved')]
    const out = pickRandomProblem(items, ['s1', 's2'])
    expect(out?.pool).toBe('all')
    expect(out?.reset).toBe(true)
    expect(out?.candidateCount).toBe(2)
  })

  it('连续抽满一轮不出现重复', () => {
    const items = [c('a'), c('b'), c('c'), c('d')]
    const drawn = new Set<string>()
    const got: string[] = []
    const rand = seq(0.1, 0.7, 0.3, 0.9, 0.5)
    // 每次抽完立刻「标记已刷」，模拟真实使用：池子随之缩小
    const remaining = new Map(items.map((it) => [it.id, it]))
    for (let i = 0; i < 4; i += 1) {
      const out = pickRandomProblem([...remaining.values()], drawn, rand)
      expect(out).not.toBeNull()
      const id = out!.item.id
      expect(got).not.toContain(id)
      got.push(id)
      drawn.add(id)
      remaining.delete(id)
    }
    expect(got).toHaveLength(4)
  })
})
