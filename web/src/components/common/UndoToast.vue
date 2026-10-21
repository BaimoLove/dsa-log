<script setup lang="ts">
import { Undo2, X } from 'lucide-vue-next'
import { useUndoStore } from '@/stores/undo'

/**
 * 撤销条：批量操作后出现在底部中间。
 *
 * 单独成一个组件而不是复用 ToastHost，是因为它要在提示条里画倒计时进度条，
 * 且必须持续显示到窗口结束（ToastHost 的普通提示 2.6s 就消失）。
 */
const undo = useUndoStore()
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200"
      leave-active-class="transition duration-150"
      enter-from-class="translate-y-3 opacity-0"
      leave-to-class="translate-y-2 opacity-0"
    >
      <div
        v-if="undo.pending"
        class="fixed inset-x-0 bottom-4 z-40 flex justify-center px-4"
        role="status"
        aria-live="polite"
      >
        <div class="relative w-full max-w-md overflow-hidden rounded-xl border border-slate-700 bg-slate-900 text-white shadow-lg">
          <div class="flex items-center gap-2.5 px-3.5 py-2.5">
            <Undo2 class="size-4 shrink-0 text-slate-300" :stroke-width="1.8" />
            <p class="flex-1 text-[13px] leading-snug">{{ undo.pending.label }}</p>
            <span class="shrink-0 text-[12px] tabular-nums text-slate-400">{{ undo.remainingSeconds }}s</span>
            <button
              type="button"
              class="shrink-0 rounded-md bg-white/10 px-2.5 py-1 text-[13px] font-medium text-white transition-colors hover:bg-white/20 disabled:opacity-50"
              :disabled="undo.busy"
              @click="undo.undo()"
            >
              撤销
            </button>
            <button
              type="button"
              class="shrink-0 rounded-md p-1 text-slate-400 transition-colors hover:text-slate-200"
              aria-label="关闭撤销提示"
              @click="undo.dismiss()"
            >
              <X class="size-3.5" />
            </button>
          </div>
          <!-- 倒计时进度：由 expiresAt 驱动，服务端把窗口调短时会跟着变 -->
          <div class="h-0.5 w-full bg-white/10">
            <div
              class="h-full bg-blue-400 transition-[width] duration-100 ease-linear"
              :style="{ width: `${undo.progress * 100}%` }"
            />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
