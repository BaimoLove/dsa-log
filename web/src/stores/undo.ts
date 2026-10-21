import { computed, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { undoRepo } from '@/repositories'
import { toAppError } from '@/types/common'
import type { BatchResult } from '@/types/repository'
import { useUiStore } from './ui'

/**
 * 撤销入口。
 *
 * 时间语义（两个数字，别混）：
 * - `UNDO_UI_MS = 6000`：提示条存活时长，超时不打扰。这是**展示窗口**。
 * - `undoExpiresAt`：服务端快照的截止时间（Redis TTL 120s），这是**能力上限**。
 *
 * 实际展示时长取两者较小值，所以倒计时仍然由 `undoExpiresAt` 兜底——
 * 若服务端把 TTL 调短，提示条会跟着提前结束，不会出现「点了却 409」的假按钮。
 */
export const UNDO_UI_MS = 6000

const TICK_MS = 100

export interface PendingUndo {
  opId: string
  label: string
  startedAt: number
  expiresAt: number
  /** 撤销成功后刷新视图用；放在这里是为了让调用方不必自己管生命周期 */
  onUndone?: () => void | Promise<void>
}

export const useUndoStore = defineStore('undo', () => {
  const ui = useUiStore()

  // shallowRef：pending 里带函数，不需要深响应；也避免 Vue 代理包住回调
  const pending = shallowRef<PendingUndo | null>(null)
  const now = shallowRef(Date.now())
  const busy = shallowRef(false)

  let timer: ReturnType<typeof setInterval> | null = null

  const remainingMs = computed(() =>
    pending.value ? Math.max(0, pending.value.expiresAt - now.value) : 0,
  )
  const remainingSeconds = computed(() => Math.ceil(remainingMs.value / 1000))
  const progress = computed(() => {
    const p = pending.value
    if (!p) return 0
    const span = p.expiresAt - p.startedAt
    return span <= 0 ? 0 : Math.max(0, Math.min(1, (p.expiresAt - now.value) / span))
  })

  function stopTimer(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function startTimer(): void {
    stopTimer()
    now.value = Date.now()
    timer = setInterval(() => {
      now.value = Date.now()
      const p = pending.value
      if (!p) {
        stopTimer()
        return
      }
      if (now.value >= p.expiresAt) dismiss()
    }, TICK_MS)
  }

  /**
   * 批量操作成功后调用。
   * 没有 opId 说明服务端快照没写成（降级分支），此时保持安静，不显示撤销入口。
   */
  function offer(result: BatchResult, label: string, onUndone?: () => void | Promise<void>): void {
    if (!result.opId || !result.undoExpiresAt) {
      dismiss()
      return
    }
    const startedAt = Date.now()
    pending.value = {
      opId: result.opId,
      label,
      startedAt,
      expiresAt: Math.min(result.undoExpiresAt, startedAt + UNDO_UI_MS),
      ...(onUndone ? { onUndone } : {}),
    }
    startTimer()
  }

  async function undo(): Promise<void> {
    const p = pending.value
    if (!p || busy.value) return
    busy.value = true
    try {
      const res = await undoRepo.undo(p.opId)
      dismiss()
      ui.success(`已撤销，还原 ${res.affected} 道`)
      await p.onUndone?.()
    } catch (e) {
      dismiss()
      ui.error(toAppError(e).message)
    } finally {
      busy.value = false
    }
  }

  function dismiss(): void {
    pending.value = null
    stopTimer()
  }

  return { pending, remainingMs, remainingSeconds, progress, busy, offer, undo, dismiss }
})
