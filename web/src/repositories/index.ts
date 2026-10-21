/**
 * 组合根：全项目唯一决定「数据从哪来」的地方。
 *
 * VITE_DATA_SOURCE=local → Dexie（IndexedDB），开发期用，接口不用起
 * VITE_DATA_SOURCE=http  → Spring Boot REST，M8 联调时接入
 *
 * 视图层与 store 只依赖 types/repository.ts 的接口，不关心这里的实现。
 */
import type { ListRepository, ProblemRepository, TagRepository, UndoRepository } from '@/types/repository'
import { dexieProblemRepository } from './dexie/problemRepository'
import { dexieTagRepository } from './dexie/tagRepository'
import { dexieListRepository } from './dexie/listRepository'
import { dexieUndoRepository } from './dexie/undoRegistry'

export type DataSource = 'local' | 'http'

export const dataSource: DataSource = import.meta.env.VITE_DATA_SOURCE === 'http' ? 'http' : 'local'

export const apiBase = import.meta.env.VITE_API_BASE || '/api'

if (dataSource === 'http') {
  // 不静默降级到本地库：那会让「数据到底存在哪」变得不可预期
  console.warn('[data] VITE_DATA_SOURCE=http，但 http 实现尚未接入（M8 联调），当前仍使用本地 IndexedDB')
}

export const problemRepo: ProblemRepository = dexieProblemRepository
export const tagRepo: TagRepository = dexieTagRepository
export const listRepo: ListRepository = dexieListRepository
export const undoRepo: UndoRepository = dexieUndoRepository

export function getDataSourceLabel(): string {
  return dataSource === 'http' ? 'HTTP 接口（Spring Boot + MySQL）' : '本地 IndexedDB（Dexie）'
}
