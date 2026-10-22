import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/db'
import { problemRepo } from '@/repositories'
import { dexieTagRepository as tagRepo } from '@/repositories/dexie/tagRepository'
import { AppError } from '@/types/common'

/**
 * 标签管理的两条硬约束（后端实现与这里必须一致）：
 * 1. 标签独立成表，题目只存 tagIds —— 所以改名只改一处，全站立刻生效。
 * 2. 删除是解绑不是级联 —— 摘掉题目上的 tagId，题目本身不动。
 *
 * 另外 usageCount 的口径是「未归档的题」，归档题不参与统计，
 * 与题库列表、随机、待复习的排除规则保持同一条。
 */
beforeEach(async () => {
  await db.open()
  await Promise.all([
    db.problems.clear(),
    db.tags.clear(),
    db.reviews.clear(),
    db.listItems.clear(),
    db.problemLists.clear(),
  ])
})

describe('标签管理', () => {
  it('新建后出现在列表里，引用数为 0', async () => {
    await tagRepo.create('动态规划', '#3b82f6')
    const tags = await tagRepo.list()

    expect(tags).toHaveLength(1)
    expect(tags[0]?.name).toBe('动态规划')
    expect(tags[0]?.color).toBe('#3b82f6')
    expect(tags[0]?.usageCount).toBe(0)
  })

  it('重名被拒绝，且忽略大小写', async () => {
    await tagRepo.create('DP', '')

    // 后端库排序规则 utf8mb4_general_ci 天然大小写不敏感，Dexie 侧靠 nameLower 对齐
    await expect(tagRepo.create('dp', '')).rejects.toBeInstanceOf(AppError)
    await expect(tagRepo.create('Dp', '')).rejects.toThrow(/已存在/)
  })

  it('改名后题目读到的是新名字（标签现拼，不需要刷题目）', async () => {
    const tag = await tagRepo.create('动态规划', '')
    const p = await problemRepo.create({ title: '最长回文子串', difficulty: 'medium', tagIds: [tag.id] })

    await tagRepo.update(tag.id, { name: 'DP' })

    const reloaded = await problemRepo.getById(p.id)
    expect(reloaded?.tags.map((t) => t.name)).toEqual(['DP'])
    // 题目表里存的始终是 id，名字不落库
    expect(reloaded?.tagIds).toEqual([tag.id])
  })

  it('引用计数跳过归档题', async () => {
    const tag = await tagRepo.create('图论', '')
    const active = await problemRepo.create({ title: '岛屿数量', difficulty: 'medium', tagIds: [tag.id] })
    const archived = await problemRepo.create({ title: '旧题', difficulty: 'easy', tagIds: [tag.id] })

    await problemRepo.update(archived.id, { archived: 1 })

    const tags = await tagRepo.list()
    expect(tags[0]?.usageCount).toBe(1)
    expect(active.id).toBeTruthy()
  })

  it('删除标签只解绑，题目本身保留', async () => {
    const tag = await tagRepo.create('双指针', '')
    const p = await problemRepo.create({ title: '三数之和', difficulty: 'medium', tagIds: [tag.id] })

    await tagRepo.remove(tag.id)

    expect(await tagRepo.list()).toHaveLength(0)
    const reloaded = await problemRepo.getById(p.id)
    expect(reloaded).not.toBeNull()
    expect(reloaded?.tagIds).toEqual([])
    expect(reloaded?.tags).toEqual([])
  })

  it('列表按引用数降序，再按名称升序', async () => {
    const hot = await tagRepo.create('热门', '')
    const warm = await tagRepo.create('中等', '')
    await tagRepo.create('冷门', '')

    await problemRepo.create({ title: 'A', difficulty: 'easy', tagIds: [hot.id] })
    await problemRepo.create({ title: 'B', difficulty: 'easy', tagIds: [hot.id] })
    await problemRepo.create({ title: 'C', difficulty: 'easy', tagIds: [warm.id] })

    const names = (await tagRepo.list()).map((t) => t.name)
    expect(names).toEqual(['热门', '中等', '冷门'])
  })

  it('keyword 只做名称过滤，不参与排序', async () => {
    await tagRepo.create('动态规划', '')
    await tagRepo.create('状压 DP', '')

    const hit = await tagRepo.list('dp')
    expect(hit.map((t) => t.name)).toEqual(['状压 DP'])
  })
})
