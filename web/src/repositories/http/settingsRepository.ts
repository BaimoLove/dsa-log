import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import type { AppSettings } from '@/types/models'
import type { SettingsRepository } from '@/types/repository'
import type { SettingsDto } from './dto'
import { toSettings } from './mapper'

/**
 * 设置仓储（HTTP）。
 *
 * 后端存的是四个 key-value 行（review.dailyLimit / review.intervals / ui.pageSize / ui.overdueFromNow），
 * 出参拼成一个对象；脏值/缺键在后端就回落到默认值了，这里只做一次结构映射。
 */
export function createHttpSettingsRepository(client: ApiClient): SettingsRepository {
  return {
    async get(): Promise<AppSettings> {
      const dto = await client.request<SettingsDto>(ENDPOINTS.settings)
      return toSettings(dto)
    },

    async save(patch: Partial<AppSettings>): Promise<AppSettings> {
      const dto = await client.request<SettingsDto>(ENDPOINTS.settings, {
        method: 'PUT',
        // undefined 的键被 JSON.stringify 丢掉 = 不改；只传用户真改的那两项也可以
        body: patch,
      })
      return toSettings(dto)
    },
  }
}
