<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CalendarClock, CheckCircle2, RotateCcw, Sparkles } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import DifficultyBadge from '@/components/problem/DifficultyBadge.vue'
import TagChip from '@/components/problem/TagChip.vue'
import { reviewRepo, settingsRepo } from '@/repositories'
import { RATINGS, RATING_LABEL, type Rating } from '@/types/enums'
import { toAppError } from '@/types/common'
import type { DueReviewItem } from '@/types/repository'
import { formatDateTime, fromNow } from '@/utils/date'

/**
 * 复习页：一次只做一道，翻面看自己的思路，然后给三档评分。
 *
 * 队列从 due 接口一次取（上限取设置里的每日上限），评分后本地前进，
 * 不每题重拉——重拉会让「刚评完的题」因为排期仍 <= now 又冒出来。
 */
const queue = ref<DueReviewItem[]>([])
const total = ref(0)
const index = ref(0)
const revealed = ref(false)
const loading = ref(true)
const busy = ref(false)
const ratedCount = ref(0)
const lastMessage = ref('')

const current = computed(() => queue.value[index.value] ?? null)
const finished = computed(() => !loading.value && queue.value.length > 0 && index.value >= queue.value.length)
const progress = computed(() =>
  queue.value.length === 0 ? 0 : Math.round((Math.min(index.value, queue.value.length) / queue.value.length) * 100),
)

const RATING_TONE: Record<Rating, string> = {
  again: 'bg-red-600 text-white hover:bg-red-700',
  hard: 'bg-amber-500 text-white hover:bg-amber-600',
  good: 'bg-emerald-600 text-white hover:bg-emerald-700',
}
const RATING_HINT: Record<Rating, string> = {
  again: '明天再来',
  hard: '阶段退一档',
  good: '阶段进一步',
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const settings = await settingsRepo.get()
    const res = await reviewRepo.due(settings.dailyReviewLimit)
    queue.value = res.items
    total.value = res.total
    index.value = 0
    revealed.value = false
    ratedCount.value = 0
    lastMessage.value = ''
  } finally {
    loading.value = false
  }
}

async function rate(rating: Rating): Promise<void> {
  const item = current.value
  if (!item || busy.value) return
  // 没翻面就评分等于瞎猜，先逼自己看一眼思路
  if (!revealed.value) {
    revealed.value = true
    return
  }

  busy.value = true
  try {
    const res = await reviewRepo.rate(item.problem.id, rating)
    ratedCount.value += 1
    lastMessage.value =
      rating === 'again'
        ? '忘了 · 明天再来'
        : `记得 · ${res.intervalDays} 天后（${formatDateTime(res.nextReviewAt)}）再见`
    index.value += 1
    revealed.value = false
  } catch (e) {
    const err = toAppError(e)
    lastMessage.value = err.message
  } finally {
    busy.value = false
  }
}

function reveal(): void {
  revealed.value = true
}

function onKeydown(e: KeyboardEvent): void {
  if (e.isComposing) return
  const el = e.target as HTMLElement | null
  if (el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) return
  if (!current.value) return

  if (e.key === ' ' || e.key === 'Enter') {
    e.preventDefault()
    reveal()
    return
  }
  const map: Record<string, Rating> = { '1': 'again', '2': 'hard', '3': 'good' }
  const rating = map[e.key]
  if (rating) {
    e.preventDefault()
    void rate(rating)
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  await load()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div>
    <PageHeader title="复习" description="1 / 3 / 7 / 15 天节奏，三档评分；空格翻面，1 / 2 / 3 评分">
      <template #actions>
        <span class="text-[12px] text-slate-500">
          待复习 <span class="font-medium tabular-nums text-slate-700">{{ total }}</span> 道
        </span>
        <BaseButton size="sm" variant="secondary" :disabled="loading" @click="load">
          <RotateCcw class="size-4" />
          重新载入
        </BaseButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="rounded-xl border border-slate-200 bg-white px-4 py-10 text-center text-[13px] text-slate-400">
      正在载入复习队列…
    </div>

    <div v-else-if="total === 0" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState
        title="今天没有要复习的题"
        description="标记已刷的题会按 1 / 3 / 7 / 15 天的节奏回到这里；先刷几道新题吧。"
      >
        <RouterLink to="/problems">
          <BaseButton size="sm" variant="primary">去题库</BaseButton>
        </RouterLink>
      </EmptyState>
    </div>

    <div v-else-if="finished" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState
        title="这一轮复习做完了"
        :description="`本轮共复习 ${ratedCount} 道。队列里还有 ${Math.max(0, total - ratedCount)} 道没做（受每日上限限制），可以再载入一轮。`"
      >
        <BaseButton size="sm" variant="primary" @click="load">
          <Sparkles class="size-4" />
          再载入一轮
        </BaseButton>
      </EmptyState>
    </div>

    <template v-else-if="current">
      <div class="mb-3 flex items-center gap-3">
        <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200">
          <div class="h-full rounded-full bg-blue-600 transition-all" :style="{ width: `${progress}%` }" />
        </div>
        <span class="shrink-0 text-[12px] tabular-nums text-slate-500">
          {{ Math.min(index + 1, queue.length) }} / {{ queue.length }}
        </span>
      </div>

      <div class="rounded-xl border border-slate-200 bg-white">
        <div class="border-b border-slate-100 px-5 py-4">
          <div class="flex flex-wrap items-center gap-2">
            <DifficultyBadge :difficulty="current.problem.difficulty" />
            <span class="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-600">
              阶段 {{ current.problem.reviewStage }} / 4
            </span>
            <span
              v-if="current.isOverdue"
              class="rounded-md bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-700 ring-1 ring-inset ring-amber-600/20"
            >
              逾期 {{ current.overdueDays }} 天
            </span>
            <span v-else class="text-[11px] text-slate-400">到期 {{ fromNow(current.dueAt) }}</span>
            <span v-if="current.problem.solveCount > 0" class="text-[11px] text-slate-400">
              · 已刷 {{ current.problem.solveCount }} 次
            </span>
          </div>

          <h3 class="mt-3 text-xl font-medium text-slate-900">{{ current.problem.title }}</h3>

          <div class="mt-2 flex flex-wrap items-center gap-1.5">
            <TagChip v-for="t in current.problem.tags" :key="t.id" :tag="t" />
            <a
              v-if="current.problem.url"
              :href="current.problem.url"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[12px] text-blue-600 hover:underline"
            >
              打开原题 ↗
            </a>
          </div>
        </div>

        <div class="px-5 py-4">
          <div v-if="!revealed" class="flex flex-col items-center gap-3 py-8">
            <p class="text-[13px] text-slate-500">先在心里过一遍思路，再翻面核对</p>
            <BaseButton variant="secondary" size="md" @click="reveal">翻面看我的思路（空格）</BaseButton>
          </div>

          <div v-else class="space-y-4">
            <section>
              <h4 class="text-[12px] font-medium uppercase tracking-wide text-slate-400">我的思路</h4>
              <pre class="mt-1.5 whitespace-pre-wrap break-words font-sans text-[13px] leading-relaxed text-slate-700">{{ current.problem.approach || '（没有记录思路）' }}</pre>
            </section>
            <section v-if="current.problem.note">
              <h4 class="text-[12px] font-medium uppercase tracking-wide text-slate-400">备注</h4>
              <p class="mt-1.5 whitespace-pre-wrap break-words text-[13px] leading-relaxed text-slate-600">{{ current.problem.note }}</p>
            </section>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-5 py-3">
          <RouterLink
            :to="`/problems/${current.problem.id}`"
            class="text-[12px] text-slate-500 hover:text-blue-600 hover:underline"
          >
            看详情
          </RouterLink>
          <div class="flex items-center gap-2">
            <!-- 用原生 button 而不是 BaseButton：三档评分各有自己的底色，
                 BaseButton 的 variant 只有 4 档，靠 :class 覆盖背景会和
                 variantClass 打架（同优先级下由 CSS 出现顺序决定胜负） -->
            <button
              v-for="(r, i) in RATINGS"
              :key="r"
              type="button"
              class="inline-flex h-8 select-none items-center gap-1.5 rounded-lg px-3 text-[13px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50"
              :class="RATING_TONE[r]"
              :disabled="busy"
              :title="RATING_HINT[r]"
              @click="rate(r)"
            >
              {{ i + 1 }} · {{ RATING_LABEL[r] }}
            </button>
          </div>
        </div>
      </div>

      <p v-if="lastMessage" class="mt-3 flex items-center gap-1.5 text-[12px] text-slate-500">
        <CheckCircle2 class="size-3.5 text-emerald-500" />
        {{ lastMessage }}
      </p>
      <p v-else class="mt-3 flex items-center gap-1.5 text-[12px] text-slate-400">
        <CalendarClock class="size-3.5" />
        评分只改排期，不改「已刷」状态；忘了的题第二天零点回到队列
      </p>
    </template>
  </div>
</template>
