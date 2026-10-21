import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { problemRepo } from '@/repositories'
import { dexieBackupRepository as backupRepo } from '@/repositories/dexie/backupRepository'
import { dexieListRepository as listRepo } from '@/repositories/dexie/listRepository'
import { dexieReviewRepository as reviewRepo } from '@/repositories/dexie/reviewRepository'
import { dexieSettingsRepository as settingsRepo } from '@/repositories/dexie/settingsRepository'
import { dexieTagRepository as tagRepo } from '@/repositories/dexie/tagRepository'
import {
  BACKUP_SCHEMA_VERSION,
  parseBackup,
  serializeBackup,
  serializeCsv,
} from '@/services/backup'
import { AppError } from '@/types/common'
import { DAY_MS } from '@/utils/date'

/**
 * M7 验收点：导出文件能被自己导回来。
 * 另外三条是真会踩的边界：replace 先清库、merge 幂等（重导同一份全 skipped）、
 * 版本不匹配直接 422 而不是尽力兼容。
 */
beforeEach(async () => {
  await db.open()
  await Promise.all([
    db.problems.clear(),
    db.tags.clear(),
    db.reviews.clear(),
    db.listItems.clear(),
    db.problemLists.clear(),
    db.settings.clear(),
  ])
})

async function seed() {
  const tag = await tagRepo.create('动态规划', '#0ea5e9')
  const p1 = await problemRepo.create({
    title: '爬楼梯',
    difficulty: 'easy',
    approach: 'dp[i] = dp[i-1] + dp[i-2]',
    note: '含,逗号 与 "引号"',
    source: 'LeetCode',
    tagIds: [tag.id],
  })
  const p2 = await problemRepo.create({ title: '最长递增子序列', difficulty: 'medium' })
  await problemRepo.markSolved(p1.id)
  await reviewRepo.rate(p1.id, 'good')

  const list = await listRepo.create({ name: '热题 100', description: '面试高频' })
  await listRepo.addProblems(list.id, [p1.id, p2.id])
  await settingsRepo.save({ dailyReviewLimit: 42, pageSize: 50 })

  return { tag, p1, p2, list }
}

describe('导出 → 导入往返', () => {
  it('导出的 JSON 能被自己解析回来，字段一个不少', async () => {
    const { p1, list } = await seed()
    const payload = await backupRepo.exportAll()

    const text = serializeBackup(payload)
    const parsed = parseBackup(text)

    expect(parsed.schemaVersion).toBe(BACKUP_SCHEMA_VERSION)
    expect(parsed.problems).toHaveLength(2)
    expect(parsed.lists).toHaveLength(1)
    expect(parsed.listItems).toHaveLength(2)
    expect(parsed.reviews.length).toBeGreaterThanOrEqual(2)
    expect(parsed.settings.dailyReviewLimit).toBe(42)

    const restored = parsed.problems.find((p) => p.id === p1.id)
    expect(restored?.title).toBe('爬楼梯')
    expect(restored?.nextReviewAt).toBeTypeOf('number')
    expect(restored?.tagIds).toHaveLength(1)
    // tags 是导出快照，导入时会丢弃重拼
    expect(restored?.tags).toEqual([])
    expect(parsed.lists[0]?.id).toBe(list.id)
  })

  it('replace 到空库后数据完全一致（题目、标签、题单进度、复习流水）', async () => {
    const { p1, list } = await seed()
    const text = serializeBackup(await backupRepo.exportAll())
    const before = {
      problems: (await problemRepo.list({ page: 1, size: 100 })).total,
      solved: (await problemRepo.list({ statuses: ['solved'], page: 1, size: 100 })).total,
      progress: (await listRepo.list())[0],
      history: (await reviewRepo.history(p1.id)).length,
    }

    // 清库再导入
    await Promise.all([
      db.problems.clear(),
      db.tags.clear(),
      db.reviews.clear(),
      db.listItems.clear(),
      db.problemLists.clear(),
    ])
    const result = await backupRepo.importAll(parseBackup(text), 'replace')

    expect(result.mode).toBe('replace')
    expect(result.problems.created).toBe(2)
    expect(result.listItems).toBe(2)

    expect((await problemRepo.list({ page: 1, size: 100 })).total).toBe(before.problems)
    expect((await problemRepo.list({ statuses: ['solved'], page: 1, size: 100 })).total).toBe(before.solved)
    const after = (await listRepo.list())[0]
    expect(after?.total).toBe(before.progress?.total)
    expect(after?.solved).toBe(before.progress?.solved)
    expect((await reviewRepo.history(p1.id)).length).toBe(before.history)
    expect((await listRepo.getById(list.id))?.name).toBe('热题 100')

    // 标签关联也回来了（标签名按 tagIds 现拼）
    const restored = await problemRepo.getById(p1.id)
    expect(restored?.tags[0]?.name).toBe('动态规划')
  })

  it('merge 重导同一份文件是幂等的：全 skipped，不产生重复', async () => {
    await seed()
    const parsed = parseBackup(serializeBackup(await backupRepo.exportAll()))

    const first = await backupRepo.importAll(parsed, 'merge')
    expect(first.problems.created).toBe(0)
    expect(first.problems.updated).toBe(0)
    expect(first.problems.skipped).toBe(2)
    expect(first.tags.skipped).toBe(1)

    const second = await backupRepo.importAll(parsed, 'merge')
    expect(second.problems.skipped).toBe(2)
    expect(await db.problems.count()).toBe(2)
    expect(await db.reviews.count()).toBe(await db.reviews.count())
  })

  it('merge 冲突按 updatedAt 取新：文件里更新才覆盖', async () => {
    const { p1 } = await seed()
    const parsed = parseBackup(serializeBackup(await backupRepo.exportAll()))

    // 文件里的版本更旧 → 跳过
    parsed.problems = parsed.problems.map((p) =>
      p.id === p1.id ? { ...p, title: '旧标题', updatedAt: p.updatedAt - DAY_MS } : p,
    )
    let result = await backupRepo.importAll(parsed, 'merge')
    expect(result.problems.skipped).toBeGreaterThanOrEqual(1)
    expect((await problemRepo.getById(p1.id))?.title).toBe('爬楼梯')

    // 文件里的版本更新 → 覆盖
    parsed.problems = parsed.problems.map((p) =>
      p.id === p1.id ? { ...p, title: '新标题', updatedAt: p.updatedAt + 2 * DAY_MS } : p,
    )
    result = await backupRepo.importAll(parsed, 'merge')
    expect(result.problems.updated).toBeGreaterThanOrEqual(1)
    expect((await problemRepo.getById(p1.id))?.title).toBe('新标题')
  })

  it('merge 不动库里的设置，replace 才覆盖', async () => {
    await seed()
    const parsed = parseBackup(serializeBackup(await backupRepo.exportAll()))
    parsed.settings.dailyReviewLimit = 7

    await backupRepo.importAll(parsed, 'merge')
    expect((await settingsRepo.get()).dailyReviewLimit).toBe(42)

    await backupRepo.importAll(parsed, 'replace')
    expect((await settingsRepo.get()).dailyReviewLimit).toBe(7)
  })

  it('合并模式即使库里是默认设置也不被文件覆盖（与后端同一条规则）', async () => {
    const parsed = parseBackup(
      JSON.stringify({
        schemaVersion: BACKUP_SCHEMA_VERSION,
        settings: { dailyReviewLimit: 7, pageSize: 50 },
      }),
    )
    await backupRepo.importAll(parsed, 'merge')
    expect((await settingsRepo.get()).dailyReviewLimit).toBe(20)
    expect((await settingsRepo.get()).pageSize).toBe(20)
  })
})

describe('备份解析的拒绝路径', () => {
  it('版本不匹配 → IMPORT_INVALID', () => {
    const text = JSON.stringify({ schemaVersion: 99, problems: [] })
    expect(() => parseBackup(text)).toThrowError(/版本不匹配/)
    try {
      parseBackup(text)
    } catch (e) {
      expect((e as AppError).code).toBe('IMPORT_INVALID')
    }
  })

  it('不是 JSON → IMPORT_INVALID', () => {
    expect(() => parseBackup('not json')).toThrowError(/不是合法的 JSON/)
  })

  it('缺字段补齐默认值，不整份拒绝', () => {
    const text = JSON.stringify({
      schemaVersion: BACKUP_SCHEMA_VERSION,
      problems: [{ id: 'p1', title: '只有标题', difficulty: 'easy' }],
    })
    const parsed = parseBackup(text)
    expect(parsed.problems[0]).toMatchObject({
      id: 'p1',
      status: 'todo',
      approach: '',
      solveCount: 0,
      reviewStage: 0,
      archived: 0,
      tagIds: [],
    })
    expect(parsed.settings.dailyReviewLimit).toBe(20)
  })

  it('难度不合法 → IMPORT_INVALID', () => {
    const text = JSON.stringify({
      schemaVersion: BACKUP_SCHEMA_VERSION,
      problems: [{ id: 'p1', title: 'x', difficulty: 'super-hard' }],
    })
    expect(() => parseBackup(text)).toThrowError(/不合法/)
  })
})

describe('CSV 导出', () => {
  it('带 BOM、表头完整、逗号与引号被转义', async () => {
    await seed()
    // CSV 只导题目主体字段，note / approach 是长文本，进表格反而难读；
    // 转义用标题来验
    await problemRepo.create({ title: '含,逗号 与 "引号"', difficulty: 'hard', source: '笔试' })

    const csv = await backupRepo.exportCsv()
    expect(csv.startsWith('\uFEFF')).toBe(true)

    const lines = csv.slice(1).split('\r\n')
    expect(lines[0]).toContain('id,title,difficulty,status,tags')
    expect(lines.some((l) => l.includes('"含,逗号 与 ""引号"""'))).toBe(true)
    const row = lines.find((l) => l.includes('爬楼梯'))
    expect(row).toContain('动态规划')
  })

  it('空表也有表头', () => {
    expect(serializeCsv([]).split('\r\n')).toHaveLength(1)
  })

  it('时间列是 ISO 而不是 epoch（CSV 给人看）', async () => {
    await seed()
    const csv = await backupRepo.exportCsv()
    expect(csv).toMatch(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}/)
  })
})
