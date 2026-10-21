import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { tagRepo } from '@/repositories'
import type { TagWithUsage } from '@/types/repository'

export const useTagStore = defineStore('tag', () => {
  const tags = ref<TagWithUsage[]>([])
  const loading = ref(false)
  const ready = ref(false)

  const byId = computed(() => new Map(tags.value.map((t) => [t.id, t])))
  const totalUsage = computed(() => tags.value.reduce((sum, t) => sum + t.usageCount, 0))

  async function load(): Promise<void> {
    loading.value = true
    try {
      tags.value = await tagRepo.list()
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  async function create(name: string, color?: string): Promise<void> {
    await tagRepo.create(name, color ?? '')
    await load()
  }

  async function update(id: string, patch: { name?: string; color?: string }): Promise<void> {
    await tagRepo.update(id, patch)
    await load()
  }

  async function remove(id: string): Promise<void> {
    await tagRepo.remove(id)
    await load()
  }

  return { tags, loading, ready, byId, totalUsage, load, create, update, remove }
})
