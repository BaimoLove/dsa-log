<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CalendarClock, CheckCircle2, Dices, Layers, ListChecks, Plus, RotateCcw } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { statsRepo, getDataSourceLabel } from '@/repositories'
import { useRandomStore } from '@/stores/random'
import { toAppError } from '@/types/common'
import type { OverviewStats } from '@/types/repository'
import { toPercent } from '@/utils/progress'

/**
 * 概览页。
 *
 * 这里只调 statsRepo.overview()——**视图层不碰 db**（M5 记的旧账）。
 * 四个数字与后端 /api/stats/overview 同源，M8 对拍比的就是这一组。
 */
const random = useRandomStore()

const stats = ref<OverviewStats | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    stats.value = await statsRepo.overview()
  } catch (e) {
    error.value = toAppError(e).message
  } finally {
    loading.value = false
  }
})

const cards = computed(() => [
  { key: 'due', label: '待复习', value: stats.value?.dueCount, icon: CalendarClock, tone: 'text-amber-600 bg-amber-50' },
  { key: 'today', label: '今日已刷', value: stats.value?.solvedToday, icon: CheckCircle2, tone: 'text-emerald-600 bg-emerald-50' },
  { key: 'reviewed', label: '今日复习', value: stats.value?.reviewedToday, icon: RotateCcw, tone: 'text-sky-600 bg-sky-50' },
  { key: 'total', label: '题目总数', value: stats.value?.totalProblems, icon: ListChecks, tone: 'text-blue-600 bg-blue-50' },
  { key: 'solved', label: '已刷题目', value: stats.value?.solvedProblems, icon: Layers, tone: 'text-violet-600 bg-violet-50' },
])

/** 只展示前 4 个题单：概览页不是看板，多了反而看不完 */
const topLists = computed(() => (stats.value?.listProgress ?? []).slice(0, 4))
</script>

<template>
  <div>
    <PageHeader title="概览" description="今天的复习任务与题库状态">
      <template #actions>
        <RouterLink to="/problems/new">
          <BaseButton variant="primary" size="sm">
            <Plus class="size-4" />
            新建题目
          </BaseButton>
        </RouterLink>
      </template>
    </PageHeader>

    <p v-if="error" class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] text-red-700">
      载入概览失败：{{ error }}
    </p>

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      <div
        v-for="c in cards"
        :key="c.key"
        class="rounded-xl border border-slate-200 bg-white px-4 py-3.5"
      >
        <div class="flex items-center gap-2">
          <div class="flex size-7 items-center justify-center rounded-lg" :class="c.tone">
            <component :is="c.icon" class="size-4" :stroke-width="1.8" />
          </div>
          <span class="text-[13px] text-slate-500">{{ c.label }}</span>
        </div>
        <p class="mt-2.5 text-2xl font-medium tabular-nums text-slate-900">
          {{ loading ? '—' : (c.value ?? 0) }}
        </p>
      </div>
    </div>

    <section v-if="topLists.length" class="mt-5 rounded-xl border border-slate-200 bg-white">
      <div class="flex items-center justify-between border-b border-slate-100 px-4 py-2.5">
        <h3 class="text-[13px] font-medium text-slate-700">题单进度</h3>
        <RouterLink to="/lists" class="text-[12px] text-blue-600 hover:underline">看全部</RouterLink>
      </div>
      <ul class="divide-y divide-slate-100">
        <li v-for="l in topLists" :key="l.id">
          <RouterLink
            :to="`/lists/${l.id}`"
            class="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-slate-50"
          >
            <span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: l.color }" />
            <span class="min-w-0 flex-1 truncate text-[13px] text-slate-700">{{ l.name }}</span>
            <div class="h-1.5 w-24 shrink-0 overflow-hidden rounded-full bg-slate-200">
              <div
                class="h-full rounded-full bg-blue-600"
                :style="{ width: `${toPercent(l.solved, l.total)}%`, backgroundColor: l.color }"
              />
            </div>
            <span class="w-14 shrink-0 text-right text-[12px] tabular-nums text-slate-500">
              {{ l.solved }}/{{ l.total }}
            </span>
          </RouterLink>
        </li>
      </ul>
    </section>

    <div class="mt-3 grid gap-3 sm:grid-cols-3">
      <RouterLink
        to="/problems"
        class="group rounded-xl border border-slate-200 bg-white px-4 py-3.5 transition-colors hover:border-blue-300 hover:bg-blue-50/40"
      >
        <p class="text-sm font-medium text-slate-800">去题库</p>
        <p class="mt-1 text-[13px] text-slate-500">录入题目、打标签、加入题单</p>
      </RouterLink>
      <button
        type="button"
        class="group rounded-xl border border-slate-200 bg-white px-4 py-3.5 text-left transition-colors hover:border-blue-300 hover:bg-blue-50/40"
        @click="random.openModal()"
      >
        <p class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <Dices class="size-4 text-blue-600" />
          随机一题
        </p>
        <p class="mt-1 text-[13px] text-slate-500">未刷优先，一轮内不重复</p>
      </button>
      <RouterLink
        to="/review"
        class="group rounded-xl border border-slate-200 bg-white px-4 py-3.5 transition-colors hover:border-blue-300 hover:bg-blue-50/40"
      >
        <p class="text-sm font-medium text-slate-800">开始复习</p>
        <p class="mt-1 text-[13px] text-slate-500">
          {{ stats && stats.dueCount > 0 ? `今天有 ${stats.dueCount} 道待复习` : '按 1/3/7/15 天节奏回顾已刷题目' }}
        </p>
      </RouterLink>
    </div>

    <p class="mt-6 text-[12px] text-slate-400">数据源：{{ getDataSourceLabel() }}</p>
  </div>
</template>
