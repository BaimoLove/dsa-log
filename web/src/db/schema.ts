import Dexie, { type EntityTable, type Table } from 'dexie'
import type { Problem, ProblemList, ListItem, ReviewRecord } from '@/types/models'

/** 标签表行：比 TagBrief 多一个用于唯一索引的小写名与使用计数缓存 */
export interface DbTag {
  id: string
  name: string
  nameLower: string
  color: string
  createdAt: number
}

export interface DbSetting {
  key: string
  value: unknown
}

/**
 * 表结构与索引的唯一真相源。
 * 规则：version() 的 stores 只增不改，新增字段在 upgrade() 里回填默认值。
 *
 * 索引说明：
 * - *tagIds      multiEntry，支持按标签 anyOf 过滤
 * - 复合索引只服务单维高频查询；多条件组合筛选走内存过滤
 */
export class DsaDb extends Dexie {
  problems!: EntityTable<Problem, 'id'>
  tags!: EntityTable<DbTag, 'id'>
  problemLists!: EntityTable<ProblemList, 'id'>
  /** 复合主键 [listId, problemId]，EntityTable 的约束表达不了，用 Table */
  listItems!: Table<ListItem, [string, string]>
  reviews!: EntityTable<ReviewRecord, 'id'>
  settings!: EntityTable<DbSetting, 'key'>

  constructor() {
    super('problem-tracker')

    this.version(1).stores({
      problems:
        'id, difficulty, status, solvedAt, nextReviewAt, createdAt, updatedAt, ' +
        '*tagIds, [status+difficulty], [status+nextReviewAt], [difficulty+updatedAt]',
      tags: 'id, &nameLower, createdAt',
      problemLists: 'id, name, sortOrder, createdAt',
      listItems: '[listId+problemId], listId, problemId, sortOrder',
      reviews: 'id, problemId, reviewedAt, [problemId+reviewedAt], nextReviewAt',
      settings: 'key',
    })
  }
}

export const db = new DsaDb()

/** 数据源不可用（无痕模式 / 用户禁用存储）时置位，视图层据此进入只读降级页 */
export const storageState = {
  available: true,
  error: null as string | null,
}

/**
 * 打开数据库并申请持久化。
 * 失败不抛异常，改为置位 storageState，由 main.ts 决定渲染降级页还是正常应用。
 */
export async function openDb(): Promise<boolean> {
  try {
    await db.open()
    storageState.available = true
    storageState.error = null
    if (navigator.storage?.persist) {
      await navigator.storage.persist().catch(() => false)
    }
    return true
  } catch (e) {
    storageState.available = false
    storageState.error = e instanceof Error ? e.message : String(e)
    return false
  }
}

/** 多标签页场景：另一个标签升级了库结构时提示刷新，避免继续用旧 schema 写坏数据 */
db.on('versionchange', () => {
  db.close()
  storageState.available = false
  storageState.error = '数据库结构已升级，请刷新页面'
})

export async function storageUsage(): Promise<{ usage: number; quota: number } | null> {
  if (!navigator.storage?.estimate) return null
  const { usage = 0, quota = 0 } = await navigator.storage.estimate()
  return { usage, quota }
}
