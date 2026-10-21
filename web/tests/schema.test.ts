import 'fake-indexeddb/auto'
import { describe, expect, it, beforeAll } from 'vitest'
import { db } from '@/db/schema'

/**
 * M1 验收：6 张表与索引必须一次建对。
 * 索引建错（例如 multiEntry 缺 *）在浏览器里是静默降级，线上才会表现为查询变慢，
 * 所以在 CI 里锁死。
 */
describe('Dexie schema', () => {
  beforeAll(async () => {
    await db.open()
  })

  it('建好 6 张表', () => {
    const names = db.tables.map((t) => t.name).sort()
    expect(names).toEqual(['listItems', 'problemLists', 'problems', 'reviews', 'settings', 'tags'])
  })

  it('problems 有状态+难度、状态+复习时间两组复合索引', () => {
    const compounds = db.problems.schema.indexes
      .filter((i) => i.compound)
      .map((i) => i.name ?? '')
    expect(compounds).toContain('[status+difficulty]')
    expect(compounds).toContain('[status+nextReviewAt]')
  })

  it('problems.tagIds 是 multiEntry 索引', () => {
    const idx = db.problems.schema.indexes.find((i) => i.name === 'tagIds')
    expect(idx?.multi).toBe(true)
  })

  it('tags.nameLower 唯一', () => {
    const idx = db.tags.schema.indexes.find((i) => i.name === 'nameLower')
    expect(idx?.unique).toBe(true)
  })

  it('listItems 用 [listId+problemId] 复合主键，天然去重', () => {
    expect(db.listItems.schema.primKey.keyPath).toEqual(['listId', 'problemId'])
  })
})
