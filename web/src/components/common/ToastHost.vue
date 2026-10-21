<script setup lang="ts">
import { CheckCircle2, CircleAlert, Info, TriangleAlert } from 'lucide-vue-next'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const styleMap = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  error: 'border-red-200 bg-red-50 text-red-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
  info: 'border-slate-200 bg-white text-slate-700',
} as const

const iconMap = {
  success: CheckCircle2,
  error: CircleAlert,
  warning: TriangleAlert,
  info: Info,
} as const
</script>

<template>
  <div class="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end">
    <TransitionGroup
      enter-active-class="transition duration-200"
      leave-active-class="transition duration-150"
      enter-from-class="translate-y-2 opacity-0"
      leave-to-class="translate-y-1 opacity-0"
    >
      <div
        v-for="t in ui.toasts"
        :key="t.id"
        class="pointer-events-auto flex w-full max-w-sm items-start gap-2.5 rounded-xl border px-3.5 py-2.5 shadow-sm"
        :class="styleMap[t.type]"
      >
        <component :is="iconMap[t.type]" class="mt-0.5 size-4 shrink-0" :stroke-width="1.8" />
        <p class="flex-1 text-[13px] leading-snug">{{ t.message }}</p>
        <button
          v-if="t.actionLabel"
          type="button"
          class="shrink-0 text-[13px] font-medium underline decoration-dotted underline-offset-2"
          @click="t.onAction?.(); ui.dismiss(t.id)"
        >
          {{ t.actionLabel }}
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
