import { db, type DbTag } from '@/db'
import { AppError } from '@/types/common'
import type { TagRepository, TagWithUsage } from '@/types/repository'
import { newId } from '@/utils/id'

/**
 * 标签仓储（M2 最小集）。
 *
 * 设计：标签独立成表，题目只存 `tagIds` 数组。
 * 于是「改名」只改一处，全站立刻生效；删除标签需要反向清理所有题目的 tagIds。
 */
const DEFAULT_COLORS = [
  '#6366f1',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#14b8a6',
  '#f43f5e',
]

export function pickTagColor(seed = Date.now()): string {
  return DEFAULT_COLORS[Math.abs(seed) % DEFAULT_COLORS.length] ?? '#6366f1'
}

async function assertNameFree(name: string, exceptId?: string): Promise<void> {
  const nameLower = name.trim().toLowerCase()
  const hit = await db.tags.where('nameLower').equals(nameLower).first()
  if (hit && hit.id !== exceptId) throw new AppError('CONFLICT', `标签「${name.trim()}」已存在`)
}

export const dexieTagRepository: TagRepository = {
  async list(keyword?: string): Promise<TagWithUsage[]> {
    const [tags, problems] = await Promise.all([db.tags.toArray(), db.problems.toArray()])
    const usage = new Map<string, number>()
    for (const p of problems) {
      if (p.archived === 1) continue
      for (const id of p.tagIds ?? []) usage.set(id, (usage.get(id) ?? 0) + 1)
    }
    const kw = (keyword ?? '').trim().toLowerCase()
    return tags
      .filter((t) => !kw || t.nameLower.includes(kw))
      .map((t) => ({
        id: t.id,
        name: t.name,
        color: t.color,
        createdAt: t.createdAt,
        usageCount: usage.get(t.id) ?? 0,
      }))
      .sort((a, b) => b.usageCount - a.usageCount || a.name.localeCompare(b.name, 'zh-CN'))
  },

  async create(name: string, color: string): Promise<DbTag> {
    const trimmed = name.trim()
    if (!trimmed) throw new AppError('VALIDATION_FAILED', '标签名不能为空')
    if (trimmed.length > 32) throw new AppError('VALIDATION_FAILED', '标签名不能超过 32 个字符')
    await assertNameFree(trimmed)
    const tag: DbTag = {
      id: newId(),
      name: trimmed,
      nameLower: trimmed.toLowerCase(),
      color: color || pickTagColor(),
      createdAt: Date.now(),
    }
    await db.tags.add(tag)
    return tag
  },

  async update(id: string, patch: { name?: string; color?: string }): Promise<DbTag> {
    const row = await db.tags.get(id)
    if (!row) throw new AppError('NOT_FOUND', '标签不存在')
    const changes: Partial<DbTag> = {}
    if (patch.name !== undefined) {
      const trimmed = patch.name.trim()
      if (!trimmed) throw new AppError('VALIDATION_FAILED', '标签名不能为空')
      await assertNameFree(trimmed, id)
      changes.name = trimmed
      changes.nameLower = trimmed.toLowerCase()
    }
    if (patch.color !== undefined) changes.color = patch.color
    await db.tags.update(id, changes)
    return { ...row, ...changes }
  },

  async remove(id: string): Promise<void> {
    await db.transaction('rw', db.tags, db.problems, async () => {
      const affected = await db.problems.toArray()
      for (const p of affected) {
        if (!(p.tagIds ?? []).includes(id)) continue
        await db.problems.update(p.id, {
          tagIds: p.tagIds.filter((t) => t !== id),
          updatedAt: Date.now(),
        })
      }
      await db.tags.delete(id)
    })
  },
}
