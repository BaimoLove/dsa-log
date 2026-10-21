import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import type { Rating } from '@/types/enums'
import type { ReviewRecord } from '@/types/models'
import type { DueReviewResult, RateResult, ReviewRepository } from '@/types/repository'
import type { DueDto, RateDto, ReviewRecordDto } from './dto'
import { toDueResult, toRateResult, toReviewRecord } from './mapper'

/**
 * 复习仓储（HTTP）。
 *
 * due 的两个数字语义不同，别合并：total 是「真正待复习多少道」，
 * limited 是「这次返回了几条」（受 limit 与设置里的每日上限约束）。
 */
export function createHttpReviewRepository(client: ApiClient): ReviewRepository {
  return {
    async due(limit?: number): Promise<DueReviewResult> {
      const dto = await client.request<DueDto>(ENDPOINTS.reviews.due, { query: { limit } })
      return toDueResult(dto)
    },

    async rate(problemId: string, rating: Rating): Promise<RateResult> {
      const dto = await client.request<RateDto>(ENDPOINTS.reviews.rate(problemId), {
        method: 'POST',
        body: { rating },
      })
      return toRateResult(dto)
    },

    async history(problemId: string): Promise<ReviewRecord[]> {
      const rows = await client.request<ReviewRecordDto[]>(ENDPOINTS.reviews.history, {
        query: { problemId },
      })
      return (rows ?? []).map(toReviewRecord)
    },
  }
}
