import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import type { OverviewStats, StatsRepository } from '@/types/repository'
import type { OverviewDto } from './dto'
import { toOverview } from './mapper'

/**
 * 概览（HTTP）。
 *
 * 五个数字与题单进度全由后端一条 /stats/overview 给出，前端不做二次计算：
 * 「概览里某题单 12/30」与「题单页 12/30」必须同源，否则迟早有一个数不对。
 */
export function createHttpStatsRepository(client: ApiClient): StatsRepository {
  return {
    async overview(): Promise<OverviewStats> {
      const dto = await client.request<OverviewDto>(ENDPOINTS.stats.overview)
      return toOverview(dto)
    },
  }
}
