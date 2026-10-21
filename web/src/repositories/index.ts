/**
 * 组合根：全项目唯一决定「数据从哪来」的地方。
 *
 * VITE_DATA_SOURCE=local → Dexie（IndexedDB），默认值，后端不用起也能开发
 * VITE_DATA_SOURCE=http  → Spring Boot REST + MySQL
 *
 * 视图层与 store 只依赖 types/repository.ts 的接口，不关心这里的实现；
 * 想知道当前连的是哪套，调 getDataSourceLabel()。
 */
import { apiBase, apiClient } from '@/api/client'
import type {
  BackupRepository,
  ListRepository,
  ProblemRepository,
  ReviewRepository,
  SettingsRepository,
  StatsRepository,
  TagRepository,
} from '@/types/repository'
import { dexieProblemRepository } from './dexie/problemRepository'
import { dexieTagRepository } from './dexie/tagRepository'
import { dexieListRepository } from './dexie/listRepository'
import { dexieReviewRepository } from './dexie/reviewRepository'
import { dexieSettingsRepository } from './dexie/settingsRepository'
import { dexieStatsRepository } from './dexie/statsRepository'
import { dexieBackupRepository } from './dexie/backupRepository'
import { createHttpProblemRepository } from './http/problemRepository'
import { createHttpTagRepository } from './http/tagRepository'
import { createHttpListRepository } from './http/listRepository'
import { createHttpReviewRepository } from './http/reviewRepository'
import { createHttpSettingsRepository } from './http/settingsRepository'
import { createHttpStatsRepository } from './http/statsRepository'
import { createHttpBackupRepository } from './http/backupRepository'

export type DataSource = 'local' | 'http'

export const dataSource: DataSource = import.meta.env.VITE_DATA_SOURCE === 'http' ? 'http' : 'local'

export { apiBase }

/**
 * 两套实现同时构造，只把选中的那套导出去。
 * 不做「http 失败自动降级到本地库」——那会让「我的数据到底存在哪」变得不可预期，
 * 而且降级后写进 IndexedDB 的操作在下次切回来时不会自动同步。
 */
const useHttp = dataSource === 'http'

export const problemRepo: ProblemRepository = useHttp
  ? createHttpProblemRepository(apiClient)
  : dexieProblemRepository
export const tagRepo: TagRepository = useHttp ? createHttpTagRepository(apiClient) : dexieTagRepository
export const listRepo: ListRepository = useHttp ? createHttpListRepository(apiClient) : dexieListRepository
export const reviewRepo: ReviewRepository = useHttp
  ? createHttpReviewRepository(apiClient)
  : dexieReviewRepository
export const settingsRepo: SettingsRepository = useHttp
  ? createHttpSettingsRepository(apiClient)
  : dexieSettingsRepository
export const statsRepo: StatsRepository = useHttp ? createHttpStatsRepository(apiClient) : dexieStatsRepository
export const backupRepo: BackupRepository = useHttp
  ? createHttpBackupRepository(apiClient)
  : dexieBackupRepository

export function getDataSourceLabel(): string {
  return useHttp ? `HTTP 接口（${apiBase} → Spring Boot + MySQL）` : '本地 IndexedDB（Dexie）'
}
