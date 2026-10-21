<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CalendarClock, CheckCircle2, Dices, Layers, ListChecks, Plus } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { db } from '@/db'
import { useRandomStore } from '@/stores/random'
import { getDataSourceLabel } from '@/repositories'

const random = useRandomStore()

const total = ref(0)
const solved = ref(0)
const due = ref(0)
const todaySolved = ref(0)
const loading = ref(true)

onMounted(async () => {
  try {
    total.value = await db.problems.count()
    solved.value = await db.problems.where('status').equals('solved').count()
    const now = Date.now()
    due.value = await db.problems
      .where('[status+nextReviewAt]')
      .between(['solved', 0], ['solved', now], true, true)
      .count()
    const start = new Date(now).setHours(0, 0, 0, 0)
    todaySolved.value = await db.problems
      .where('lastSolvedAt')
      .between(start, now, true, true)
      .count()
  } finally {
    loading.value = false
  }
})

const cards = [
  { key: 'due', label: '待复习', icon: CalendarClock, tone: 'text-amber-600 bg-amber-50' },
  { key: 'today', label: '今日已刷', icon: CheckCircle2, tone: 'text-emerald-600 bg-emerald-50' },
  { key: 'total', label: '题目总数', icon: ListChecks, tone: 'text-blue-600 bg-blue-50' },
  { key: 'solved', label: '已刷题目', icon: Layers, tone: 'text-violet-600 bg-violet-50' },
] as const

const valueOf = (key: string) =>
  key === 'due' ? due.value : key === 'today' ? todaySolved.value : key === 'total' ? total.value : solved.value
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

    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
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
          {{ loading ? '—' : valueOf(c.key) }}
        </p>
      </div>
    </div>

    <div class="mt-5 grid gap-3 sm:grid-cols-3">
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
        <p class="mt-1 text-[13px] text-slate-500">按 1/3/7/15 天节奏回顾已刷题目</p>
      </RouterLink>
    </div>

    <p class="mt-6 text-[12px] text-slate-400">数据源：{{ getDataSourceLabel() }}</p>
  </div>
</template>
