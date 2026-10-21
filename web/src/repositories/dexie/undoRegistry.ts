import { AppError } from '@/types/common'
import type { UndoRepository } from '@/types/repository'
import { newId } from '@/utils/id'

/**
 * 本地撤销快照登记处（Dexie 阶段的替代实现）。
 *
 * 生产环境撤销由后端 Redis 承担（`dsa:undo:{opId}`，120s TTL），前端只拿 opId。
 * 开发期没有后端，就用内存里的 Map 顶替：契约完全一致 —— 批量操作返回
 * `{opId, undoExpiresAt}`，撤销时按 opId 找回快照逆序回滚。
 * 于是 `stores/undo.ts` 只需要调 `UndoRepository` 接口，M8 切 http 时不用改一行。
 *
 * 有意不落 IndexedDB：撤销窗口只有 2 分钟，刷新后丢掉是可接受的，
 * 落库反而要处理「过期行清理」这个 Redis 用 TTL 免掉的问题。
 */

/** 与后端 Redis 快照 TTL 对齐 */
export const UNDO_TTL_MS = 120_000

interface UndoEntry {
  opId: string
  label: string
  createdAt: number
  expiresAt: number
  /** 逆序回滚，返回受影响行数 */
  revert: () => Promise<number>
}

export interface UndoToken {
  opId: string
  undoExpiresAt: number
}

const registry = new Map<string, UndoEntry>()

/** 惰性清理：只在登记与撤销时扫一遍，不启定时器 */
function pruneExpired(now: number = Date.now()): void {
  for (const [opId, entry] of registry) {
    if (entry.expiresAt <= now) registry.delete(opId)
  }
}

/**
 * 登记一份撤销快照。调用方必须已经算好 `revert` 所需的旧值。
 * revert 抛错时快照已被消耗，不提供重试——与后端「一次性撤销」一致。
 */
export function registerUndo(
  label: string,
  revert: () => Promise<number>,
  ttlMs: number = UNDO_TTL_MS,
): UndoToken {
  pruneExpired()
  const now = Date.now()
  const opId = newId()
  registry.set(opId, { opId, label, createdAt: now, expiresAt: now + ttlMs, revert })
  return { opId, undoExpiresAt: now + ttlMs }
}

/** 仅供测试与调试：查看当前可撤销数量 */
export function pendingUndoCount(): number {
  pruneExpired()
  return registry.size
}

/** 仅供测试：清空登记处，避免用例之间互相污染 */
export function resetUndoRegistry(): void {
  registry.clear()
}

export const dexieUndoRepository: UndoRepository = {
  async undo(opId: string): Promise<{ affected: number }> {
    pruneExpired()
    const entry = registry.get(opId)
    if (!entry) throw new AppError('UNDO_EXPIRED', '撤销已过期，可在列表中手动改回')
    // 先摘掉再回滚：重复点击的第二次请求会走上面的分支，不会重复回滚
    registry.delete(opId)
    const affected = await entry.revert()
    return { affected }
  },
}
