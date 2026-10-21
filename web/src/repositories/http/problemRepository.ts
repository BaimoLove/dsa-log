import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import { AppError } from '@/types/common'
import type { Problem } from '@/types/models'
import type {
  BatchResult,
  CreateProblemInput,
  MarkSolvedResult,
  Paginated,
  ProblemQuery,
  ProblemRepository,
  RandomPickResult,
  UpdateProblemInput,
} from '@/types/repository'
import type { BatchDto, MarkSolvedDto, PageDto, ProblemDto, RandomPickDto } from './dto'
import { toMarkSolved, toPaginated, toProblem, toRandomPick } from './mapper'

/**
 * HTTPS 版题目仓储。
 *
 * 与 Dexie 版逐条对齐的几处语义（M8 对拍时逐项验过）：
 * - getById：不存在与「已归档」都返回 null。后端把归档当 404，这里把 404 收敛成 null，
 *   因为接口契约是 `Problem | null` 而不是抛错。
 * - update：不因「刚被归档」而失败——后端 update 不回读 get，直接返回更新后的题目。
 * - 批量已刷/取消/打标签：后端 SQL 自带 archived = 0，affected 天然不含归档题。
 * - 批量删除：后端刻意不带 archived 条件，归档题也能删。
 */
export function createHttpProblemRepository(client: ApiClient): ProblemRepository {
  return {
    async list(query: ProblemQuery): Promise<Paginated<Problem>> {
      const dto = await client.request<PageDto<ProblemDto>>(ENDPOINTS.problems.list, {
        query: {
          keyword: query.keyword,
          difficulties: query.difficulties,
          statuses: query.statuses,
          tagIds: query.tagIds,
          listId: query.listId,
          // 默认 0：不传也要显式发，后端按精确匹配处理（0 = 只看未归档）
          archived: query.archived ?? 0,
          page: query.page ?? 1,
          size: query.size ?? 20,
          sort: query.sort,
        },
      })
      return toPaginated(dto, toProblem)
    },

    async getById(id: string) {
      try {
        const dto = await client.request<ProblemDto>(ENDPOINTS.problems.detail(id))
        return toProblem(dto)
      } catch (e) {
        if (e instanceof AppError && e.code === 'NOT_FOUND') return null
        throw e
      }
    },

    async create(input: CreateProblemInput) {
      const dto = await client.request<ProblemDto>(ENDPOINTS.problems.list, {
        method: 'POST',
        body: {
          title: input.title,
          difficulty: input.difficulty,
          approach: input.approach,
          note: input.note,
          source: input.source,
          url: input.url,
          tagIds: input.tagIds,
        },
      })
      return toProblem(dto)
    },

    async update(id: string, patch: UpdateProblemInput) {
      // 直接透传 patch：undefined 的键会被 JSON.stringify 丢掉，正好等于「不改」
      const dto = await client.request<ProblemDto>(ENDPOINTS.problems.detail(id), {
        method: 'PATCH',
        body: patch,
      })
      return toProblem(dto)
    },

    async remove(id: string): Promise<void> {
      await client.request<void>(ENDPOINTS.problems.detail(id), { method: 'DELETE' })
    },

    async markSolved(id: string): Promise<MarkSolvedResult> {
      const dto = await client.request<MarkSolvedDto>(ENDPOINTS.problems.markSolved(id), { method: 'POST' })
      return toMarkSolved(dto)
    },

    async markUnsolved(id: string) {
      const dto = await client.request<ProblemDto>(ENDPOINTS.problems.markUnsolved(id), { method: 'POST' })
      return toProblem(dto)
    },

    async bulkMarkSolved(ids: string[]): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.problems.bulk.solved, { method: 'POST', body: { ids } })
    },

    async bulkMarkUnsolved(ids: string[]): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.problems.bulk.unsolved, { method: 'POST', body: { ids } })
    },

    async bulkAddTags(ids: string[], tagIds: string[]): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.problems.bulk.tags, {
        method: 'POST',
        body: { ids, tagIds },
      })
    },

    async bulkAddToList(ids: string[], listId: string): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.problems.bulk.lists, {
        method: 'POST',
        body: { ids, listId },
      })
    },

    async bulkRemove(ids: string[]): Promise<BatchResult> {
      return client.request<BatchDto>(ENDPOINTS.problems.bulk.remove, { method: 'POST', body: { ids } })
    },

    async random(excludeIds: string[]): Promise<RandomPickResult | null> {
      // 数组按重复 key 拼：?exclude=a&exclude=b，Spring 的 List<String> 认这种写法
      const dto = await client.request<RandomPickDto | null>(ENDPOINTS.problems.random, {
        query: { exclude: excludeIds },
      })
      // 题库为空时后端返 data:null（不是 204），这里直接传下去
      return dto == null ? null : toRandomPick(dto)
    },
  }
}
