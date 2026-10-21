import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { problemRepo } from '@/repositories'
import { useProblemStore } from '@/stores/problem'
import type { Problem } from '@/types/models'
import type { RandomPool } from '@/services/randomPicker'

/**
 * 随机一题的会话状态。
 *
 * `drawnIds` 只存内存：按需求「一轮内不重复、不跨刷新」——
 * 刷新页面等于开启新的一轮，这是刻意的。
 */
export const useRandomStore = defineStore('random', () => {
  const open = ref(false)
  const loading = ref(false)
  const current = ref<Problem | null>(null)
  const pool = ref<RandomPool>('unsolved')
  const drawnIds = ref<Set<string>>(new Set())
  const lastReset = ref(false)
  const candidateCount = ref(0)
  const empty = ref(false)

  const poolHint = computed(() => {
    if (empty.value) return '题库还是空的'
    if (lastReset.value) {
      return pool.value === 'unsolved' ? '未刷的题已抽完，本轮已重置' : '题库已抽完一轮，重新开始'
    }
    return pool.value === 'unsolved'
      ? `从 ${candidateCount.value} 道未刷题中抽取`
      : `未刷已抽完，改从全库 ${candidateCount.value} 道题中抽取`
  })

  async function draw(): Promise<void> {
    loading.value = true
    try {
      const res = await problemRepo.random([...drawnIds.value])
      if (!res) {
        current.value = null
        empty.value = true
        lastReset.value = false
        candidateCount.value = 0
        return
      }
      // 池子被重置说明上一轮已抽完，画一条新的一轮
      if (res.reset) drawnIds.value = new Set()
      drawnIds.value.add(res.problem.id)

      current.value = res.problem
      pool.value = res.pool
      lastReset.value = res.reset
      candidateCount.value = res.candidateCount
      empty.value = false
    } finally {
      loading.value = false
    }
  }

  function openModal(): void {
    open.value = true
    if (!current.value || empty.value) void draw()
  }

  function close(): void {
    open.value = false
  }

  /** 卡片上直接标记已刷：写入后同步题目列表里的同一条 */
  async function markCurrentSolved(): Promise<Problem | null> {
    if (!current.value) return null
    const res = await problemRepo.markSolved(current.value.id)
    current.value = res.problem
    useProblemStore().patchLocal(res.problem)
    return res.problem
  }

  function resetSession(): void {
    drawnIds.value = new Set()
    current.value = null
    lastReset.value = false
    empty.value = false
  }

  return {
    open,
    loading,
    current,
    pool,
    drawnIds,
    lastReset,
    candidateCount,
    empty,
    poolHint,
    draw,
    openModal,
    close,
    markCurrentSolved,
    resetSession,
  }
})
