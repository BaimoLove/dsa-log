import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listRepo } from '@/repositories'
import type { BatchResult, ListProgress, ProblemListBasic } from '@/types/repository'

export const useListStore = defineStore('list', () => {
  const lists = ref<ListProgress[]>([])
  const loading = ref(false)
  const ready = ref(false)

  const totalSolved = computed(() => lists.value.reduce((sum, l) => sum + l.solved, 0))
  const totalItems = computed(() => lists.value.reduce((sum, l) => sum + l.total, 0))

  function byId(id: string): ListProgress | undefined {
    return lists.value.find((l) => l.id === id)
  }

  async function load(): Promise<void> {
    loading.value = true
    try {
      lists.value = await listRepo.list()
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  async function create(input: { name: string; description?: string; color?: string }): Promise<ProblemListBasic> {
    const created = await listRepo.create(input)
    await load()
    return created
  }

  async function update(
    id: string,
    patch: { name?: string; description?: string; color?: string },
  ): Promise<ProblemListBasic> {
    const updated = await listRepo.update(id, patch)
    await load()
    return updated
  }

  async function remove(id: string): Promise<void> {
    await listRepo.remove(id)
    await load()
  }

  async function addProblems(listId: string, problemIds: string[]): Promise<BatchResult> {
    const res = await listRepo.addProblems(listId, problemIds)
    await load()
    return res
  }

  async function removeProblem(listId: string, problemId: string): Promise<void> {
    await listRepo.removeProblem(listId, problemId)
    await load()
  }

  return {
    lists,
    loading,
    ready,
    totalSolved,
    totalItems,
    byId,
    load,
    create,
    update,
    remove,
    addProblems,
    removeProblem,
  }
})
