import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import type { TagRepository } from '@/types/repository'
import type { TagUsageDto } from './dto'
import { toTagWithUsage } from './mapper'

/** 标签仓储（HTTP）。写操作返回 TagUsageVO，但契约只要 id/name/color，多的字段丢掉即可 */
export function createHttpTagRepository(client: ApiClient): TagRepository {
  return {
    async list(keyword?: string) {
      const rows = await client.request<TagUsageDto[]>(ENDPOINTS.tags.list, { query: { keyword } })
      return (rows ?? []).map(toTagWithUsage)
    },

    async create(name: string, color: string) {
      const dto = await client.request<TagUsageDto>(ENDPOINTS.tags.list, {
        method: 'POST',
        body: { name, color },
      })
      return { id: dto.id, name: dto.name, color: dto.color }
    },

    async update(id: string, patch: { name?: string; color?: string }) {
      const dto = await client.request<TagUsageDto>(ENDPOINTS.tags.detail(id), {
        method: 'PATCH',
        body: patch,
      })
      return { id: dto.id, name: dto.name, color: dto.color }
    },

    async remove(id: string): Promise<void> {
      await client.request<void>(ENDPOINTS.tags.detail(id), { method: 'DELETE' })
    },
  }
}
