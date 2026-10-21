import 'fake-indexeddb/auto'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createApiClient } from '@/api/client'
import { db } from '@/db'
import { dexieBackupRepository } from '@/repositories/dexie/backupRepository'
import { dexieListRepository } from '@/repositories/dexie/listRepository'
import { dexieProblemRepository } from '@/repositories/dexie/problemRepository'
import { dexieReviewRepository } from '@/repositories/dexie/reviewRepository'
import { dexieSettingsRepository } from '@/repositories/dexie/settingsRepository'
import { dexieStatsRepository } from '@/repositories/dexie/statsRepository'
import { dexieTagRepository } from '@/repositories/dexie/tagRepository'
import { createHttpBackupRepository } from '@/repositories/http/backupRepository'
import { createHttpListRepository } from '@/repositories/http/listRepository'
import { createHttpProblemRepository } from '@/repositories/http/problemRepository'
import { createHttpReviewRepository } from '@/repositories/http/reviewRepository'
import { createHttpSettingsRepository } from '@/repositories/http/settingsRepository'
import { createHttpStatsRepository } from '@/repositories/http/statsRepository'
import { createHttpTagRepository } from '@/repositories/http/tagRepository'
import { DEFAULT_SETTINGS } from '@/types/models'
import type { AppSettings, Problem } from '@/types/models'
import type {
  BackupPayload,
  BackupRepository,
  ListRepository,
  ProblemRepository,
  ReviewRepository,
  SettingsRepository,
  StatsRepository,
  TagRepository,
} from '@/types/repository'

/**
 * M8 双跑对拍：同一组操作在 Dexie 与 HTTP 两套实现上各跑一遍，
 * 比较**与 id、绝对时间无关**的指标。
 *
 * 为什么不是逐字段比对象：两边的 id 各自生成（Dexie 用时间戳基数、后端用 UUID），
 * 时间戳也必然差几毫秒。所以每题用 title 做稳定键，时间只比「排期落在今天 +N 天」。
 *
 * 跑法（需要后端在 8080 且库可写）：
 *   cd web && PARITY=1 npx vitest run tests/httpParity.test.ts
 *
 * 安全措施：开始时把后端整库导出到内存，结束时 replace 还原。
 * 即便如此，**别把它指向正式库**——中途崩溃会留下测试数据。
 */
const BASE = process.env.PARITY_BASE ?? 'http://127.0.0.1:8080/api'
/**
 * 开关有两处来源：环境变量 PARITY=1，或 `npm run test:parity`（--mode parity）。
 * 刻意不做「连不上就自动跳过」：跳过等于假绿，对拍本身就是为了发现差异。
 */
const enabled = process.env.PARITY === '1' || import.meta.env.MODE === 'parity'
const DAY = 86_400_000

interface Repos {
  problems: ProblemRepository
  tags: TagRepository
  lists: ListRepository
  reviews: ReviewRepository
  settings: SettingsRepository
  stats: StatsRepository
  backup: BackupRepository
}

const dexieRepos: Repos = {
  problems: dexieProblemRepository,
  tags: dexieTagRepository,
  lists: dexieListRepository,
  reviews: dexieReviewRepository,
  settings: dexieSettingsRepository,
  stats: dexieStatsRepository,
  backup: dexieBackupRepository,
}

const httpClient = createApiClient({ baseUrl: BASE })
const httpRepos: Repos = {
  problems: createHttpProblemRepository(httpClient),
  tags: createHttpTagRepository(httpClient),
  lists: createHttpListRepository(httpClient),
  reviews: createHttpReviewRepository(httpClient),
  settings: createHttpSettingsRepository(httpClient),
  stats: createHttpStatsRepository(httpClient),
  backup: createHttpBackupRepository(httpClient),
}

/** 拍平的快照：字段全部可比较，不含 id 与绝对时间戳 */
interface Snapshot {
  problems: Array<{
    title: string
    difficulty: string
    status: string
    archived: number
    solveCount: number
    reviewStage: number
    inPool: boolean
    dueInDays: number | null
    tags: string[]
  }>
  queries: Record<string, { total: number; titles: string[] }>
  affected: Record<string, number>
  random: { pool: string; reset: boolean; candidateCount: number; title: string } | null
  due: { total: number; limited: number; titles: string[]; overdueDays: number[] }
  rate: { fromStage: number; toStage: number; intervalDays: number; dueInDays: number } | null
  historyKinds: string[]
  overview: {
    dueCount: number
    solvedToday: number
    reviewedToday: number
    totalProblems: number
    solvedProblems: number
  }
  listProgress: Array<{ name: string; total: number; solved: number }>
  tagUsage: Array<{ name: string; usageCount: number }>
  listMembers: string[]
  getByIdMissing: boolean
  getByIdArchived: boolean
  settings: Pick<AppSettings, 'dailyReviewLimit' | 'pageSize'>
}

function emptySettings(): AppSettings {
  return { ...DEFAULT_SETTINGS, reviewIntervals: [...DEFAULT_SETTINGS.reviewIntervals] }
}

function emptyPayload(): BackupPayload {
  return {
    app: 'dsa-log',
    schemaVersion: 1,
    exportedAt: Date.now(),
    problems: [],
    tags: [],
    lists: [],
    listItems: [],
    reviews: [],
    settings: emptySettings(),
  }
}

function problemFixture(id: string, title: string, patch: Partial<Problem>): Problem {
  const now = Date.now()
  return {
    id,
    title,
    difficulty: 'medium',
    status: 'solved',
    approach: '',
    note: '',
    source: '',
    url: '',
    tagIds: [],
    tags: [],
    solvedAt: now - 5 * DAY,
    lastSolvedAt: now - 5 * DAY,
    solveCount: 1,
    reviewStage: 1,
    nextReviewAt: null,
    lastReviewedAt: null,
    archived: 0,
    createdAt: now - 5 * DAY,
    updatedAt: now - 5 * DAY,
    ...patch,
  }
}

/** 排期落在「今天 +N 天」（本地日界），只有这种相对量可以跨实现比较 */
function dayOffset(at: number | null): number | null {
  if (at == null) return null
  const startOf = (t: number) => {
    const d = new Date(t)
    d.setHours(0, 0, 0, 0)
    return d.getTime()
  }
  return Math.round((startOf(at) - startOf(Date.now())) / DAY)
}

function titles(items: ReadonlyArray<{ title: string }>): string[] {
  return items.map((p) => p.title)
}

/** 第一段：CRUD / 批量 / 题单 / 归档 / 删除 */
async function runMainScenario(r: Repos): Promise<Snapshot> {
  await r.backup.importAll(emptyPayload(), 'replace')

  const t1 = await r.tags.create('数组', '#ef4444')
  const t2 = await r.tags.create('动态规划', '#3b82f6')

  // 每次 create 之间隔几毫秒：createdAt 相同的话，MySQL 的 ORDER BY created_at
  // 没有 tiebreaker，返回顺序就是不确定的，那样比的不是排序语义而是运气。
  // 中文标题走 localeCompare 排序，两侧都在 node 里排，顺序一致。
  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
  const p1 = await r.problems.create({ title: '两数之和', difficulty: 'easy', tagIds: [t1.id] })
  await sleep(5)
  const p2 = await r.problems.create({ title: '最长回文子串', difficulty: 'medium' })
  await sleep(5)
  const p3 = await r.problems.create({ title: '接雨水', difficulty: 'hard' })
  await sleep(5)
  const p4 = await r.problems.create({ title: '合并区间', difficulty: 'medium' })

  // 操作序列：首次已刷 → 批量已刷 → 批量取消 → 重复取消（幂等 0）
  const firstSolve = await r.problems.markSolved(p1.id)
  const bulkSolved = await r.problems.bulkMarkSolved([p2.id, p3.id])
  const bulkUnsolved = await r.problems.bulkMarkUnsolved([p3.id])
  const bulkUnsolvedAgain = await r.problems.bulkMarkUnsolved([p3.id])

  // 打标签：第一次 2 道有变更，第二次幂等 0
  const addTags = await r.problems.bulkAddTags([p1.id, p2.id], [t2.id])
  const addTagsAgain = await r.problems.bulkAddTags([p1.id], [t2.id])

  const list = await r.lists.create({ name: '热题 100', color: '#10b981' })
  const addToList = await r.lists.addProblems(list.id, [p1.id, p2.id, p3.id])
  const addToListAgain = await r.lists.addProblems(list.id, [p1.id])

  // 归档 p4，删除 p3
  await r.problems.update(p4.id, { archived: 1 })
  const archivedRow = await r.problems.update(p1.id, { note: '再刷一遍' })
  await r.problems.remove(p3.id)

  // 归档题的 getById 应当是 null；不存在的 id 也是 null
  const getByIdArchived = (await r.problems.getById(p4.id)) === null
  const getByIdMissing = (await r.problems.getById('no-such-id')) === null

  // 设置：改完读回来
  const saved = await r.settings.save({ dailyReviewLimit: 30 })

  const active = await r.problems.list({ size: 100 })
  const archived = await r.problems.list({ size: 100, archived: 1 })
  const merged = [...active.items, ...archived.items].sort((a, b) => a.title.localeCompare(b.title))

  // 筛选：标签按名字换成本次运行的 id，总再用 title 比
  const tagIdOf = async (name: string) => (await r.tags.list(name))[0]?.id ?? ''
  const arrayTagId = await tagIdOf('数组')

  const queries: Snapshot['queries'] = {}
  /**
   * ordered = true 只在「排序键唯一」的查询上用。
   * 批量操作会把一批题的 updated_at 设成同一个时间戳（后端 touchBatch 只取一次 now），
   * 这些题在 updatedAt_desc 下是并列关系，MySQL 与 Array.sort 给的先后本就不同。
   * 拿并列排序去对拍，只会得到随机失败。
   */
  const runQuery = async (
    name: string,
    q: Parameters<ProblemRepository['list']>[0],
    ordered = false,
  ) => {
    const page = await r.problems.list({ size: 100, ...q })
    const list = titles(page.items)
    queries[name] = { total: page.total, titles: ordered ? list : [...list].sort() }
  }
  await runQuery('all', {})
  await runQuery('keyword', { keyword: '数' })
  await runQuery('difficulty', { difficulties: ['medium'] })
  await runQuery('statusSolved', { statuses: ['solved'] })
  await runQuery('tagAnd', { tagIds: [arrayTagId] })
  await runQuery('inList', { listId: list.id })
  // createdAt 在这次场景里逐题拉开过，排序键唯一，可以比顺序
  await runQuery('sortedCreatedAsc', { sort: 'createdAt_asc' }, true)

  const overview = await r.stats.overview()
  const lists = await r.lists.list()
  const tags = await r.tags.list()
  const random = await r.problems.random([])

  // 成员 id 两侧不同，映射成标题再比（p3 已删，映射不到就保留原 id 让差异暴露出来）
  const idToTitle = new Map(merged.map((p) => [p.id, p.title]))
  const members = (await r.lists.problemIdsOf(list.id)).map((id) => idToTitle.get(id) ?? id)

  return {
    problems: merged.map((p) => ({
      title: p.title,
      difficulty: p.difficulty,
      status: p.status,
      archived: p.archived,
      solveCount: p.solveCount,
      reviewStage: p.reviewStage,
      inPool: p.nextReviewAt != null,
      dueInDays: dayOffset(p.nextReviewAt),
      tags: [...p.tags.map((t) => t.name)].sort(),
    })),
    queries,
    affected: {
      firstSolve: firstSolve.firstSolve ? 1 : 0,
      bulkSolved: bulkSolved.affected,
      bulkUnsolved: bulkUnsolved.affected,
      bulkUnsolvedAgain: bulkUnsolvedAgain.affected,
      addTags: addTags.affected,
      addTagsAgain: addTagsAgain.affected,
      addToList: addToList.affected,
      addToListAgain: addToListAgain.affected,
      // 归档后 update 仍要返回更新后的题目，不能因为「刚被归档」而 404
      archivedUpdateNote: archivedRow.note,
    },
    random: random
      ? {
          pool: random.pool,
          reset: random.reset,
          candidateCount: random.candidateCount,
          // 均匀随机不可比，只比「抽到的题在候选池里」这件事由 candidateCount 体现
          title: random.problem.title,
        }
      : null,
    due: { total: 0, limited: 0, titles: [], overdueDays: [] },
    rate: null,
    historyKinds: [],
    overview: {
      dueCount: overview.dueCount,
      solvedToday: overview.solvedToday,
      reviewedToday: overview.reviewedToday,
      totalProblems: overview.totalProblems,
      solvedProblems: overview.solvedProblems,
    },
    listProgress: lists.map((l) => ({ name: l.name, total: l.total, solved: l.solved })),
    tagUsage: tags.map((t) => ({ name: t.name, usageCount: t.usageCount })),
    listMembers: members,
    getByIdMissing,
    getByIdArchived,
    settings: { dailyReviewLimit: saved.dailyReviewLimit, pageSize: saved.pageSize },
  } as Snapshot
}

/** 第二段：导入带固定 id 的 fixture，比对 due / rate / 概览 */
async function runReviewScenario(r: Repos): Promise<Snapshot> {
  const now = Date.now()
  const payload: BackupPayload = {
    ...emptyPayload(),
    problems: [
      // 已到期，普通题
      problemFixture('fx-1', '到期一', { reviewStage: 1, nextReviewAt: now - 2 * DAY, difficulty: 'easy' }),
      // 已到期，普通题（晚一天）
      problemFixture('fx-2', '到期二', { reviewStage: 2, nextReviewAt: now - DAY, difficulty: 'hard' }),
      // 未到期
      problemFixture('fx-3', '未到期', { reviewStage: 1, nextReviewAt: now + 3 * DAY }),
      // 已到期但已归档：不进队列、不计统计
      problemFixture('fx-4', '归档到期', { reviewStage: 1, nextReviewAt: now - DAY, archived: 1 }),
      // 未刷：不计入 solvedProblems
      problemFixture('fx-5', '没刷过', { status: 'todo', solvedAt: null, lastSolvedAt: null, solveCount: 0, reviewStage: 0 }),
    ],
    lists: [
      {
        id: 'fx-list',
        name: '固定题单',
        description: '',
        color: '#6366f1',
        sortOrder: 0,
        createdAt: now - 5 * DAY,
        updatedAt: now - 5 * DAY,
      },
    ],
    listItems: [
      { listId: 'fx-list', problemId: 'fx-1', sortOrder: 0, addedAt: now - 5 * DAY },
      { listId: 'fx-list', problemId: 'fx-4', sortOrder: 1, addedAt: now - 5 * DAY },
    ],
    reviews: [
      {
        id: 'fx-r1',
        problemId: 'fx-1',
        kind: 'MARK_SOLVED',
        rating: null,
        fromStage: 0,
        toStage: 1,
        intervalDays: 1,
        nextReviewAt: now - 2 * DAY,
        reviewedAt: now - 5 * DAY,
        note: '',
      },
    ],
  }
  await r.backup.importAll(payload, 'replace')

  const due = await r.reviews.due(2)
  const rated = await r.reviews.rate('fx-1', 'good')
  const history = await r.reviews.history('fx-1')
  const overview = await r.stats.overview()
  const lists = await r.lists.list()
  const active = await r.problems.list({ size: 100 })
  const archived = await r.problems.list({ size: 100, archived: 1 })
  const merged = [...active.items, ...archived.items].sort((a, b) => a.title.localeCompare(b.title))

  return {
    problems: merged.map((p) => ({
      title: p.title,
      difficulty: p.difficulty,
      status: p.status,
      archived: p.archived,
      solveCount: p.solveCount,
      reviewStage: p.reviewStage,
      inPool: p.nextReviewAt != null,
      dueInDays: dayOffset(p.nextReviewAt),
      tags: [...p.tags.map((t) => t.name)].sort(),
    })),
    queries: {},
    affected: {},
    random: null,
    due: {
      total: due.total,
      limited: due.limited,
      titles: due.items.map((i) => i.problem.title),
      overdueDays: due.items.map((i) => i.overdueDays),
    },
    rate: {
      fromStage: rated.fromStage,
      toStage: rated.toStage,
      intervalDays: rated.intervalDays,
      dueInDays: dayOffset(rated.nextReviewAt) ?? -999,
    },
    historyKinds: history.map((h) => h.kind),
    overview: {
      dueCount: overview.dueCount,
      solvedToday: overview.solvedToday,
      reviewedToday: overview.reviewedToday,
      totalProblems: overview.totalProblems,
      solvedProblems: overview.solvedProblems,
    },
    listProgress: lists.map((l) => ({ name: l.name, total: l.total, solved: l.solved })),
    tagUsage: [],
    listMembers: await r.lists.problemIdsOf('fx-list'),
    getByIdMissing: (await r.problems.getById('fx-none')) === null,
    getByIdArchived: (await r.problems.getById('fx-4')) === null,
    settings: { dailyReviewLimit: 20, pageSize: 20 },
  }
}

let reachable = false
let originalBackup: BackupPayload | null = null

beforeAll(async () => {
  if (!enabled) return
  await db.open()
  // 连不上就让用例失败并说清原因，而不是静默跳过——跳过等于假绿
  originalBackup = await httpRepos.backup.exportAll()
  reachable = true
})

afterAll(async () => {
  if (!enabled || !reachable || !originalBackup) return
  // 还原开始时导出的整库，避免把测试数据留在开发库里
  await httpRepos.backup.importAll(originalBackup, 'replace')
})

describe.skipIf(!enabled)('M8 双跑对拍', () => {
  it('CRUD / 批量 / 题单 / 归档 / 删除：两套实现语义一致', async () => {
    await Promise.all([
      db.problems.clear(),
      db.tags.clear(),
      db.reviews.clear(),
      db.listItems.clear(),
      db.problemLists.clear(),
      db.settings.clear(),
    ])

    const dexieSnap = await runMainScenario(dexieRepos)
    const httpSnap = await runMainScenario(httpRepos)

    // 随机抽到的题每次都不同，单独拎出来比「落在候选池里」
    expect(httpSnap.random?.pool).toBe(dexieSnap.random?.pool)
    expect(httpSnap.random?.reset).toBe(dexieSnap.random?.reset)
    expect(httpSnap.random?.candidateCount).toBe(dexieSnap.random?.candidateCount)
    const comparable = { ...httpSnap, random: null }
    expect(comparable).toEqual({ ...dexieSnap, random: null })
  })

  it('导入 fixture 后 due / rate / 概览：两套实现语义一致', async () => {
    const dexieSnap = await runReviewScenario(dexieRepos)
    const httpSnap = await runReviewScenario(httpRepos)
    expect(httpSnap).toEqual(dexieSnap)
  })

  /**
   * M8 的根本验收点是「清空浏览器缓存后数据仍在」。
   * 没法在单测里真的清浏览器，就用等价条件：http 模式下写入后，
   * IndexedDB 里必须一条业务数据都没有——那清缓存自然什么都不丢。
   */
  it('http 模式不往 IndexedDB 落业务数据（清缓存不丢数据的等价验收）', async () => {
    await Promise.all([
      db.problems.clear(),
      db.tags.clear(),
      db.reviews.clear(),
      db.listItems.clear(),
      db.problemLists.clear(),
    ])

    const created = await httpRepos.problems.create({ title: '只存在服务端的题', difficulty: 'easy' })
    expect(created.id).toBeTruthy()

    // 本地六张表仍然全空
    expect(await db.problems.count()).toBe(0)
    expect(await db.tags.count()).toBe(0)
    expect(await db.reviews.count()).toBe(0)
    expect(await db.listItems.count()).toBe(0)
    expect(await db.problemLists.count()).toBe(0)

    // 而换一个「全新的仓储实例」也能读到——数据只可能来自 MySQL
    const fresh = createHttpProblemRepository(createApiClient({ baseUrl: BASE }))
    expect((await fresh.getById(created.id))?.title).toBe('只存在服务端的题')
  })
})
