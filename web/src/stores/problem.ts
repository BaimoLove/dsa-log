import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { problemRepo } from '@/repositories'
import type { CreateProblemInput, ProblemQuery, UpdateProblemInput } from '@/types/repository'
import type { Problem } from '@/types/models'
import type { SortOption } from '@/types/enums'

export const useProblemStore = defineStore('problem', () => {
  const items = ref<Problem[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const totalPages = ref(1)
  const loading = ref(false)
  const ready = ref(false)

  /** 只保留 M2 需要的三个维度；M4 再扩到完整筛选 */
  const query = reactive<ProblemQuery>({
    keyword: '',
    statuses: [],
    difficulties: [],
    tagIds: [],
    sort: 'updatedAt_desc',
  })

  async function load(): Promise<void> {
    loading.value = true
    try {
      const res = await problemRepo.list({ ...query, page: page.value, size: size.value })
      items.value = res.items
      total.value = res.total
      totalPages.value = res.totalPages
      // 删到当前页为空时自动回退一页，避免停在空白页
      if (res.items.length === 0 && page.value > 1 && res.total > 0) {
        page.value = Math.min(res.totalPages, page.value - 1)
        await load()
      }
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  function setStatuses(statuses: ProblemQuery['statuses']): void {
    query.statuses = statuses ?? []
    page.value = 1
  }

  function setKeyword(keyword: string): void {
    query.keyword = keyword
    page.value = 1
  }

  function setSort(sort: SortOption): void {
    query.sort = sort
    page.value = 1
  }

  function setPage(next: number): void {
    page.value = Math.min(Math.max(1, next), totalPages.value)
  }

  /** 本地替换一条，用于标记已刷等原地更新，避免整页重拉 */
  function patchLocal(problem: Problem): void {
    const idx = items.value.findIndex((p) => p.id === problem.id)
    if (idx >= 0) items.value[idx] = problem
  }

  function dropLocal(id: string): void {
    const before = items.value.length
    items.value = items.value.filter((p) => p.id !== id)
    if (items.value.length < before) total.value = Math.max(0, total.value - 1)
  }

  async function fetchById(id: string): Promise<Problem | null> {
    return problemRepo.getById(id)
  }

  async function createProblem(input: CreateProblemInput): Promise<Problem> {
    const created = await problemRepo.create(input)
    page.value = 1
    await load()
    return created
  }

  async function updateProblem(id: string, patch: UpdateProblemInput): Promise<Problem> {
    const updated = await problemRepo.update(id, patch)
    patchLocal(updated)
    return updated
  }

  async function removeProblem(id: string): Promise<void> {
    await problemRepo.remove(id)
    dropLocal(id)
    await load()
  }

  async function markSolved(id: string): Promise<Problem> {
    const res = await problemRepo.markSolved(id)
    patchLocal(res.problem)
    return res.problem
  }

  async function markUnsolved(id: string): Promise<Problem> {
    const updated = await problemRepo.markUnsolved(id)
    patchLocal(updated)
    return updated
  }

  return {
    items,
    total,
    page,
    size,
    totalPages,
    loading,
    ready,
    query,
    load,
    setStatuses,
    setKeyword,
    setSort,
    setPage,
    patchLocal,
    dropLocal,
    fetchById,
    createProblem,
    updateProblem,
    removeProblem,
    markSolved,
    markUnsolved,
  }
})
