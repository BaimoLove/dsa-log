<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ExternalLink, Pencil, Trash2 } from 'lucide-vue-next'
import BaseButton from '@/components/common/BaseButton.vue'
import DifficultyBadge from './DifficultyBadge.vue'
import TagChip from './TagChip.vue'
import type { Problem } from '@/types/models'
import { formatDate, fromNow } from '@/utils/date'

const props = defineProps<{ problem: Problem; busy?: boolean }>()

const emit = defineEmits<{
  solve: [id: string]
  unsolve: [id: string]
  remove: [problem: Problem]
}>()

const solved = computed(() => props.problem.status === 'solved')

const meta = computed(() => {
  const p = props.problem
  if (p.status === 'solved') {
    return `已刷 ${p.solveCount} 次 · 上次 ${formatDate(p.lastSolvedAt)}（${fromNow(p.lastSolvedAt)}）`
  }
  return p.solveCount > 0 ? `曾刷过 ${p.solveCount} 次 · 当前标记为未刷` : '还没刷过'
})
</script>

<template>
  <div class="flex flex-col gap-2.5 px-4 py-3 transition-colors hover:bg-slate-50/70 sm:flex-row sm:items-center sm:gap-4">
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2">
        <RouterLink
          :to="`/problems/${problem.id}`"
          class="max-w-full truncate text-sm font-medium text-slate-900 hover:text-blue-600"
        >
          {{ problem.title }}
        </RouterLink>
        <DifficultyBadge :difficulty="problem.difficulty" />
        <a
          v-if="problem.url"
          :href="problem.url"
          target="_blank"
          rel="noopener noreferrer"
          class="text-slate-300 transition-colors hover:text-blue-500"
          title="打开原题链接"
        >
          <ExternalLink class="size-3.5" />
        </a>
        <span
          class="inline-flex items-center rounded-md px-1.5 py-0.5 text-[11px] font-medium"
          :class="solved ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-500'"
        >
          {{ solved ? '已刷' : '未刷' }}
        </span>
      </div>

      <div class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1">
        <TagChip v-for="t in problem.tags" :key="t.id" :tag="t" />
        <span v-if="problem.source" class="text-[12px] text-slate-400">{{ problem.source }}</span>
        <span class="text-[12px] text-slate-300">|</span>
        <span class="text-[12px] text-slate-400">{{ meta }}</span>
      </div>
    </div>

    <div class="flex shrink-0 items-center gap-1.5">
      <BaseButton v-if="!solved" size="sm" variant="secondary" :disabled="busy" @click="emit('solve', problem.id)">
        标记已刷
      </BaseButton>
      <BaseButton v-else size="sm" variant="ghost" :disabled="busy" @click="emit('solve', problem.id)">
        又刷了一次
      </BaseButton>
      <RouterLink :to="`/problems/${problem.id}/edit`">
        <BaseButton size="sm" variant="ghost" title="编辑">
          <Pencil class="size-3.5" />
        </BaseButton>
      </RouterLink>
      <BaseButton size="sm" variant="ghost" title="删除" @click="emit('remove', problem)">
        <Trash2 class="size-3.5 text-slate-400" />
      </BaseButton>
    </div>
  </div>
</template>
