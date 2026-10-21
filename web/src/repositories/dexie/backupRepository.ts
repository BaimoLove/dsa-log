import { db } from '@/db'
import { serializeCsv } from '@/services/backup'
import { AppError } from '@/types/common'
import { DEFAULT_SETTINGS, type AppSettings, type Problem } from '@/types/models'
import type {
  BackupPayload,
  BackupRepository,
  ImportCounts,
  ImportMode,
  ImportResult,
} from '@/types/repository'
import { BACKUP_APP, BACKUP_SCHEMA_VERSION } from '@/services/backup'
import { dexieSettingsRepository as settingsRepo } from './settingsRepository'

/**
 * 备份仓储（Dexie）。
 *
 * merge 与 replace 的差别只有一句「先清不清空」，其余走同一条写入逻辑，
 * 免得两种模式对「已存在同 id 怎么办」各有各的理解。
 *
 * 冲突判定统一用 updatedAt（标签没有 updatedAt，用 name+color 直接覆盖）：
 * - 文件里的更新 → 覆盖
 * - 库里的更新 → 跳过（计 skipped，不报错）
 * - 时间相同 → 跳过（幂等重导同一份文件应该得到全 skipped）
 */
const EMPTY_COUNTS = (): ImportCounts => ({ created: 0, updated: 0, skipped: 0 })

async function tagMap(): Promise<Map<string, { id: string; name: string; color: string }>> {
  const tags = await db.tags.toArray()
  return new Map(tags.map((t) => [t.id, { id: t.id, name: t.name, color: t.color }]))
}

export const dexieBackupRepository: BackupRepository = {
  async exportAll(): Promise<BackupPayload> {
    const [problems, tags, lists, listItems, reviews, settings] = await Promise.all([
      db.problems.toArray(),
      db.tags.toArray(),
      db.problemLists.toArray(),
      db.listItems.toArray(),
      db.reviews.toArray(),
      settingsRepo.get(),
    ])

    const map = await tagMap()
    return {
      app: BACKUP_APP,
      schemaVersion: BACKUP_SCHEMA_VERSION,
      exportedAt: Date.now(),
      // 导出时把标签详情拼上，文件单独拿出来也能看懂；导入时会被丢弃重拼
      problems: problems.map((p) => ({
        ...p,
        tagIds: p.tagIds ?? [],
        tags: (p.tagIds ?? []).map((id) => map.get(id)).filter((t): t is NonNullable<typeof t> => Boolean(t)),
      })),
      tags: tags.map((t) => ({ id: t.id, name: t.name, color: t.color, createdAt: t.createdAt })),
      lists,
      listItems,
      reviews,
      settings,
    }
  },

  async exportCsv(): Promise<string> {
    const [rows, map] = await Promise.all([db.problems.toArray(), tagMap()])
    const problems: Problem[] = rows.map((p) => ({
      ...p,
      tags: (p.tagIds ?? []).map((id) => map.get(id)).filter((t): t is NonNullable<typeof t> => Boolean(t)),
    }))
    return serializeCsv(problems)
  },

  async importAll(payload: BackupPayload, mode: ImportMode): Promise<ImportResult> {
    if (mode !== 'merge' && mode !== 'replace') {
      throw new AppError('VALIDATION_FAILED', '导入模式只能是 merge 或 replace')
    }

    const result: ImportResult = {
      mode,
      problems: EMPTY_COUNTS(),
      tags: EMPTY_COUNTS(),
      lists: EMPTY_COUNTS(),
      listItems: 0,
      reviews: 0,
    }

    await db.transaction(
      'rw',
      [db.problems, db.tags, db.problemLists, db.listItems, db.reviews, db.settings],
      async () => {
        if (mode === 'replace') {
          await Promise.all([
            db.problems.clear(),
            db.tags.clear(),
            db.problemLists.clear(),
            db.listItems.clear(),
            db.reviews.clear(),
            db.settings.clear(),
          ])
        }

        for (const tag of payload.tags) {
          const existing = await db.tags.get(tag.id)
          if (!existing) {
            await db.tags.add({
              id: tag.id,
              name: tag.name,
              nameLower: tag.name.toLowerCase(),
              color: tag.color || '#6366f1',
              createdAt: tag.createdAt || Date.now(),
            })
            result.tags.created += 1
            continue
          }
          if (existing.name === tag.name && existing.color === tag.color) {
            result.tags.skipped += 1
            continue
          }
          await db.tags.put({ ...existing, name: tag.name, nameLower: tag.name.toLowerCase(), color: tag.color })
          result.tags.updated += 1
        }

        for (const list of payload.lists) {
          const existing = await db.problemLists.get(list.id)
          if (!existing) {
            await db.problemLists.add(list)
            result.lists.created += 1
            continue
          }
          if ((existing.updatedAt ?? 0) >= (list.updatedAt ?? 0)) {
            result.lists.skipped += 1
            continue
          }
          await db.problemLists.put(list)
          result.lists.updated += 1
        }

        for (const p of payload.problems) {
          const incoming: Problem = { ...p, tags: [], tagIds: p.tagIds ?? [] }
          const existing = await db.problems.get(p.id)
          if (!existing) {
            await db.problems.add(incoming)
            result.problems.created += 1
            continue
          }
          if ((existing.updatedAt ?? 0) >= (incoming.updatedAt ?? 0)) {
            result.problems.skipped += 1
            continue
          }
          await db.problems.put(incoming)
          result.problems.updated += 1
        }

        // 关联表是复合主键，put 天然幂等，不需要 conflict 判断
        for (const item of payload.listItems) {
          await db.listItems.put(item)
          result.listItems += 1
        }

        for (const review of payload.reviews) {
          if (await db.reviews.get(review.id)) continue
          await db.reviews.add(review)
          result.reviews += 1
        }

        // 设置只在 merge 且库里还是默认值时采用文件里的；replace 时直接写入
        const current = await settingsRepo.get()
        const isDefault = JSON.stringify(current) === JSON.stringify(DEFAULT_SETTINGS)
        if (mode === 'replace' || isDefault) {
          await db.settings.put({ key: 'app', value: payload.settings as AppSettings })
        }
      },
    )

    return result
  },
}
