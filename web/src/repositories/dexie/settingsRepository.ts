import { db } from '@/db'
import { DEFAULT_SETTINGS, type AppSettings } from '@/types/models'
import type { SettingsRepository } from '@/types/repository'

/**
 * 设置仓储（Dexie）。
 *
 * 整份设置存一行（key='app'）而不是每个字段一行：设置永远整体读取、
 * 整体覆盖，拆成多行只会多出「读到一半」的中间态。
 * 读取时与 DEFAULT_SETTINGS 合并，旧备份里缺的字段自动补默认值。
 */
const SETTINGS_KEY = 'app'

function normalize(raw: unknown): AppSettings {
  const value = (raw ?? {}) as Partial<AppSettings>
  const intervals = Array.isArray(value.reviewIntervals)
    ? value.reviewIntervals.filter((n): n is number => typeof n === 'number' && n > 0)
    : []
  return {
    dailyReviewLimit:
      typeof value.dailyReviewLimit === 'number' && value.dailyReviewLimit > 0
        ? Math.min(200, Math.floor(value.dailyReviewLimit))
        : DEFAULT_SETTINGS.dailyReviewLimit,
    pageSize:
      typeof value.pageSize === 'number' && value.pageSize > 0
        ? Math.min(100, Math.floor(value.pageSize))
        : DEFAULT_SETTINGS.pageSize,
    reviewIntervals: intervals.length ? intervals : [...DEFAULT_SETTINGS.reviewIntervals],
    overdueFromNow: value.overdueFromNow !== false,
  }
}

export const dexieSettingsRepository: SettingsRepository = {
  async get(): Promise<AppSettings> {
    const row = await db.settings.get(SETTINGS_KEY)
    return normalize(row?.value)
  },

  async save(patch: Partial<AppSettings>): Promise<AppSettings> {
    const current = await this.get()
    const next = normalize({ ...current, ...patch })
    await db.settings.put({ key: SETTINGS_KEY, value: next })
    return next
  },
}
