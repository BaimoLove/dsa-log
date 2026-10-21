import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { problemRepo } from '@/repositories'
import { dexieReviewRepository as reviewRepo } from '@/repositories/dexie/reviewRepository'
import { dexieStatsRepository as statsRepo } from '@/repositories/dexie/statsRepository'
import { DAY_MS, tomorrowStart } from '@/utils/date'

/**
 * M6 验收点：
 * - good 后阶段 +1，下次到期日 = now + intervalOf(新阶段)
 * - again 后 nextReviewAt 是次日 00:00（本地时区），且当天不再出现在队列里
 * - due 的 total 不受 limit 影响
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

/** 把某题的排期改成过去，模拟「已到期」 */
async function makeDue(id: string, daysAgo = 1): Promise<void> {
  await db.problems.update(id, { nextReviewAt: Date.now() - daysAgo * DAY_MS })
}

async function seedSolved(title = '两数之和') {
  const p = await problemRepo.create({ title, difficulty: 'easy' })
  await problemRepo.markSolved(p.id)
  return p
}

describe('待复习队列', () => {
  it('刚标记已刷的题不在队列里（排到 1 天后）', async () => {
    await seedSolved()
    const res = await reviewRepo.due()
    expect(res.total).toBe(0)
    expect(res.items).toEqual([])
  })

  it('只收已刷且排期已到的题，未刷题即使 nextReviewAt 有值也不进队列', async () => {
    const solved = await seedSolved()
    const todo = await problemRepo.create({ title: '爬楼梯', difficulty: 'easy' })
    await makeDue(solved.id)
    // 手工给未刷题塞一个过期排期，模拟脏数据
    await db.problems.update(todo.id, { nextReviewAt: Date.now() - DAY_MS })

    const res = await reviewRepo.due()
    expect(res.total).toBe(1)
    expect(res.items[0]?.problem.id).toBe(solved.id)
  })

  it('逾期天数与 isOverdue 按自然日算', async () => {
    const p = await seedSolved()
    await makeDue(p.id, 3)

    const res = await reviewRepo.due()
    expect(res.items[0]?.isOverdue).toBe(true)
    expect(res.items[0]?.overdueDays).toBe(3)
  })

  it('total 不受 limit 影响，items 被截断', async () => {
    const created = []
    for (let i = 0; i < 5; i += 1) created.push(await seedSolved(`题 ${i}`))
    for (const p of created) await makeDue(p.id)

    const res = await reviewRepo.due(2)
    expect(res.total).toBe(5)
    expect(res.limited).toBe(2)
    expect(res.items).toHaveLength(2)
  })

  it('按到期时间升序，越早到期越靠前', async () => {
    const a = await seedSolved('晚到期')
    const b = await seedSolved('早到期')
    await makeDue(a.id, 1)
    await makeDue(b.id, 5)

    const res = await reviewRepo.due()
    expect(res.items.map((i) => i.problem.title)).toEqual(['早到期', '晚到期'])
  })
})

describe('三档评分', () => {
  it('good：阶段 +1，下次到期 = now + intervalOf(新阶段)', async () => {
    const p = await seedSolved()
    const before = Date.now()

    const res = await reviewRepo.rate(p.id, 'good')
    expect(res.fromStage).toBe(0)
    expect(res.toStage).toBe(1)
    expect(res.intervalDays).toBe(3)
    // 1 天 = 86400000，允许 2s 抖动
    expect(Math.abs(res.nextReviewAt - (before + 3 * DAY_MS))).toBeLessThan(2000)

    const fresh = await problemRepo.getById(p.id)
    expect(fresh?.reviewStage).toBe(1)
    expect(fresh?.nextReviewAt).toBe(res.nextReviewAt)
    expect(fresh?.lastReviewedAt).not.toBeNull()
  })

  it('hard：阶段回退一档，到期按新阶段算', async () => {
    const p = await seedSolved()
    await reviewRepo.rate(p.id, 'good') // stage 1
    await reviewRepo.rate(p.id, 'good') // stage 2
    const res = await reviewRepo.rate(p.id, 'hard')
    expect(res.fromStage).toBe(2)
    expect(res.toStage).toBe(1)
    expect(res.intervalDays).toBe(3)
  })

  it('good 封顶在 stage 4，间隔复用 15 天', async () => {
    const p = await seedSolved()
    await db.problems.update(p.id, { reviewStage: 4 })
    const res = await reviewRepo.rate(p.id, 'good')
    expect(res.toStage).toBe(4)
    expect(res.intervalDays).toBe(15)
  })

  it('again：阶段归零，下次是次日 00:00 且当天不再回队列', async () => {
    const p = await seedSolved()
    await reviewRepo.rate(p.id, 'good')

    const res = await reviewRepo.rate(p.id, 'again')
    expect(res.toStage).toBe(0)
    expect(res.nextReviewAt).toBe(tomorrowStart())
    expect((await reviewRepo.due()).total).toBe(0)
  })

  it('评分写一条 REVIEW 流水，带评分与前后阶段', async () => {
    const p = await seedSolved()
    await reviewRepo.rate(p.id, 'hard')

    const history = await reviewRepo.history(p.id)
    const review = history.find((r) => r.kind === 'REVIEW')
    expect(review).toBeTruthy()
    expect(review?.rating).toBe('hard')
    expect(review?.fromStage).toBe(0)
    expect(review?.toStage).toBe(0)
    expect(review?.intervalDays).toBe(1)
  })

  it('不在复习池的题拒绝评分，且不写流水', async () => {
    const p = await problemRepo.create({ title: '未刷题', difficulty: 'easy' })
    await expect(reviewRepo.rate(p.id, 'good')).rejects.toThrow('不在复习队列')

    const history = await reviewRepo.history(p.id)
    expect(history.filter((r) => r.kind === 'REVIEW')).toHaveLength(0)
  })

  it('历史按时间倒序', async () => {
    const p = await seedSolved()
    await reviewRepo.rate(p.id, 'good')
    await reviewRepo.rate(p.id, 'hard')
    const history = await reviewRepo.history(p.id)
    expect(history[0]?.kind).toBe('REVIEW')
    expect(history[0]?.rating).toBe('hard')
    expect(history.at(-1)?.kind).toBe('MARK_SOLVED')
  })
})

describe('概览指标', () => {
  it('五个数字同源：待复习、今日已刷（去重）、今日复习（条数）、总数、已刷数', async () => {
    const a = await seedSolved('A')
    const b = await seedSolved('B')
    await problemRepo.create({ title: 'C', difficulty: 'medium' })
    // 同一题再刷一次：solvedToday 仍算 1 道
    await problemRepo.markSolved(a.id)
    await makeDue(a.id)

    const stats = await statsRepo.overview()
    expect(stats.totalProblems).toBe(3)
    expect(stats.solvedProblems).toBe(2)
    expect(stats.solvedToday).toBe(2)
    expect(stats.dueCount).toBe(1)

    await reviewRepo.rate(a.id, 'good')
    const after = await statsRepo.overview()
    expect(after.reviewedToday).toBe(1)
    expect(after.solvedToday).toBe(2)
    expect(b.id).toBeTruthy()
  })

  it('逻辑删的题不计入总数与待复习', async () => {
    const p = await seedSolved()
    await makeDue(p.id)
    await db.problems.update(p.id, { archived: 1 })

    const stats = await statsRepo.overview()
    expect(stats.totalProblems).toBe(0)
    expect(stats.solvedProblems).toBe(0)
    expect(stats.dueCount).toBe(0)
  })

  it('题单进度随概览一起下发', async () => {
    const p = await seedSolved()
    const list = await db.problemLists.add({
      id: 'l1',
      name: '热题',
      description: '',
      color: '#6366f1',
      sortOrder: 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
    expect(list).toBe('l1')
    await db.listItems.put({ listId: 'l1', problemId: p.id, sortOrder: 0, addedAt: Date.now() })

    const stats = await statsRepo.overview()
    expect(stats.listProgress).toHaveLength(1)
    expect(stats.listProgress[0]).toMatchObject({ total: 1, solved: 1 })
  })
})
