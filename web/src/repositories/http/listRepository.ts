import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import { AppError } from '@/types/common'
import type { BatchResult, ListProgress, ListRepository, ProblemListBasic } from '@/types/repository'
import type { BatchDto, ListDto } from './dto'
import { toListProgress } from './mapper'

/**
 * 题单仓储（HTTP）。
 *
 * 三条与 Dexie 对齐的行为差异点：
 * - getById 对不存在返回 null（后端 404 收敛成 null），不是抛错。
 * - removeProblem 在后端对「不在题单内」返 404，Dexie 侧也抛 NOT_FOUND，两边一致，不额外兜。
 * - problemIdsOf 走专用端点 `/lists/{id}/problem-ids`：题单内的分页接口 size 上限 100，
 *   翻页取全集既慢又要处理边界，不如一条只查关联表的 SQL。
 */
export function createHttpListRepository(client: ApiClient): ListRepository {
  const basic = (dto: ListDto): ProblemListBasic => {
    const p = toListProgress(dto)
    return { id: p.id, name: p.name, description: p.description, color: p.color, sortOrder: p.sortOrder }
  }

  return {
    async list(): Promise<ListProgress[]> {
      const rows = await client.request<ListDto[]>(ENDPOINTS.lists.list)
      return (rows ?? []).map(toListProgress)
    },

    async getById(id: string) {
      try {
        const dto = await client.request<ListDto>(ENDPOINTS.lists.detail(id))
        return basic(dto)
      } catch (e) {
        if (e instanceof AppError && e.code === 'NOT_FOUND') return null
        throw e
      }
    },

    async create(input) {
      const dto = await client.request<ListDto>(ENDPOINTS.lists.list, {
        method: 'POST',
        body: { name: input.name, description: input.description, color: input.color },
      })
      return basic(dto)
    },

    async update(id, patch) {
      const dto = await client.request<ListDto>(ENDPOINTS.lists.detail(id), {
        method: 'PATCH',
        body: patch,
      })
      return basic(dto)
    },

    async remove(id: string): Promise<void> {
      await client.request<void>(ENDPOINTS.lists.detail(id), { method: 'DELETE' })
    },

    async addProblems(listId: string, problemIds: string[]): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.lists.items(listId), {
        method: 'POST',
        body: { ids: problemIds },
      })
    },

    async removeProblem(listId: string, problemId: string): Promise<void> {
      await client.request<void>(ENDPOINTS.lists.item(listId, problemId), { method: 'DELETE' })
    },

    async problemIdsOf(listId: string): Promise<string[]> {
      const ids = await client.request<string[]>(ENDPOINTS.lists.problemIds(listId))
      return ids ?? []
    },
  }
}
