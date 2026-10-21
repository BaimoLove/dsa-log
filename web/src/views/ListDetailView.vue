<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ListX } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import ProblemRow from '@/components/problem/ProblemRow.vue'
import { useListStore } from '@/stores/list'
import { useProblemStore } from '@/stores/problem'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'
import type { ProblemStatus } from '@/types/enums'
import { emptyFilter, parseProblemFilter } from '@/utils/problemQuery'
import { PROGRESS_LABEL, progressBucket, toPercent } from '@/utils/progress'

/**
 * 题单详情：进度环 + 状态筛选 + 分页。
 *
 * 筛选状态同样走 URL（`?status=todo`），与题库页共用 `utils/problemQuery`，
 * 所以从「未刷」切到「已刷」再刷新，页面还停在「已刷」。
 * 页码仍然只存在 store 里——它属于浏览位置，不属于筛选条件。
 */
const TABS: { key: 'all' | 'todo' | 'solved'; label: string; statuses: ProblemStatus[] }[] = [
  { key: 'all', label: '全部', statuses: [] },
  { key: 'todo', label: '未刷', statuses: ['todo'] },
  { key: 'solved', label: '已刷', statuses: ['solved'] },
]

const route = useRoute()
const router = useRouter()
const listStore = useListStore()
const store = useProblemStore()
const ui = useUiStore()

const listId = computed(() => String(route.params.id ?? ''))
const list = computed(() => listStore.byId(listId.value))

const statuses = computed(() => parseProblemFilter(route.query).statuses)
const activeTab = computed(() => (statuses.value.length === 1 ? statuses.value[0] : 'all'))

/** 三个 tab 的计数直接来自题单进度（现算不缓存），与环上的数字同源 */
const counts = computed(() => {
  const total = list.value?.total ?? 0
  const solved = list.value?.solved ?? 0
  return { all: total, todo: Math.max(0, total - solved), solved }
})

const percent = computed(() => toPercent(list.value?.solved ?? 0, list.value?.total ?? 0))
const bucket = computed(() => progressBucket(list.value?.solved ?? 0, list.value?.total ?? 0))
const remaining = computed(() => Math.max(0, (list.value?.total ?? 0) - (list.value?.solved ?? 0)))

const busyId = ref<string | null>(null)
const pendingRemove = ref<Problem | null>(null)
const removing = ref(false)

const rangeText = computed(() => {
  if (store.total === 0) return '共 0 道'
  const from = (store.page - 1) * store.size + 1
  const to = Math.min(store.total, store.page * store.size)
  return `第 ${from}–${to} 道 / 共 ${store.total} 道`
})

const emptyState = computed(() => {
  if (activeTab.value === 'todo') {
    return {
      title: '这个题单的题都刷完了',
      description: '切到「已刷」看看刷过的，或者去题库往题单里再加几道',
      action: 'all' as const,
      actionLabel: '看全部',
    }
  }
  if (activeTab.value === 'solved') {
    return {
      title: '还没有刷过这里的题',
      description: '先挑一道开始，标记已刷后会出现在这里',
      action: 'todo' as const,
      actionLabel: '看未刷',
    }
  }
  return {
    title: '这个题单还没有题目',
    description: '去题库页勾选题目，用「加入题单」批量放进来',
    action: 'browse' as const,
    actionLabel: '去题库挑题',
  }
})

/** URL 或题单变化时重建查询；页码随之回到第 1 页 */
async function bind(): Promise<void> {
  if (route.name !== 'list-detail') return
  store.setListId(listId.value)
  store.applyFilter({ ...emptyFilter(), statuses: statuses.value })
  store.setPage(1)
  await Promise.all([listStore.load(), store.load()])
}

watch(
  () => route.fullPath,
  () => void bind(),
  { immediate: true },
)

onBeforeUnmount(() => {
  // 复位，否则回到题库页会继续被这个题单和它的筛选条件限制
  store.setListId(undefined)
  store.resetFilters()
})

function switchTab(key: string): void {
  const tab = TABS.find((t) => t.key === key)
  if (!tab) return
  void router.replace({
    name: 'list-detail',
    params: { id: listId.value },
    query: tab.statuses.length ? { status: tab.statuses.join(',') } : {},
  })
}

function prevPage(): void {
  store.setPage(store.page - 1)
  void store.load()
}

function nextPage(): void {
  store.setPage(store.page + 1)
  void store.load()
}

async function solve(id: string): Promise<void> {
  busyId.value = id
  try {
    const updated = await store.markSolved(id)
    ui.success(`已标记刷过（累计 ${updated.solveCount} 次）`)
    // 「未刷」筛选下这一条要立刻消失；进度环也要跟着走
    if (statuses.value.length) await store.load()
    await listStore.load()
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busyId.value = null
  }
}

function askRemove(problem: Problem): void {
  pendingRemove.value = problem
}

async function confirmRemove(): Promise<void> {
  if (!pendingRemove.value) return
  removing.value = true
  try {
    await listStore.removeProblem(listId.value, pendingRemove.value.id)
    ui.success('已移出题单')
    pendingRemove.value = null
    await store.load()
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader :title="list?.name ?? '题单详情'" :description="list?.description || '题单内题目与进度'">
      <template #actions>
        <RouterLink to="/lists">
          <BaseButton size="sm" variant="secondary">
            <ArrowLeft class="size-4" />
            返回题单
          </BaseButton>
        </RouterLink>
      </template>
    </PageHeader>

    <div
      v-if="!listStore.ready"
      class="rounded-xl border border-slate-200 bg-white py-16 text-center text-sm text-slate-400"
    >
      加载中…
    </div>

    <div v-else-if="!list" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="题单不存在" description="它可能已被删除">
        <RouterLink to="/lists">
          <BaseButton size="sm" variant="secondary">返回题单列表</BaseButton>
        </RouterLink>
      </EmptyState>
    </div>

    <template v-else>
      <!-- 进度：环 + 三行账，数字与 tab 计数同源 -->
      <div class="mb-3 flex flex-wrap items-center gap-4 rounded-xl border border-slate-200 bg-white px-4 py-4">
        <ProgressRing :solved="list.solved" :total="list.total" :color="list.color" :size="72" :stroke="7">
          <span class="text-[15px] font-semibold tabular-nums text-slate-800">{{ percent }}%</span>
          <span class="mt-1 text-[10px] tabular-nums text-slate-400">{{ list.solved }}/{{ list.total }}</span>
        </ProgressRing>

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="size-2.5 rounded-full" :style="{ backgroundColor: list.color }" />
            <span class="text-[13px] font-medium text-slate-700">{{ PROGRESS_LABEL[bucket] }}</span>
          </div>
          <p class="mt-1.5 text-[12px] tabular-nums leading-relaxed text-slate-500">
            已刷 {{ list.solved }} 道 · 未刷 {{ remaining }} 道
            <span v-if="list.total > 0 && remaining === 0" class="text-emerald-600">· 这个题单清完了</span>
          </p>
        </div>
      </div>

      <div class="mb-3 flex items-center justify-between gap-3">
        <div class="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
          <button
            v-for="tab in TABS"
            :key="tab.key"
            type="button"
            class="rounded-md px-3 py-1.5 text-[13px] transition-colors"
            :class="
              activeTab === tab.key
                ? 'bg-white font-medium text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            "
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
            <span class="ml-0.5 text-[11px] tabular-nums text-slate-400">{{ counts[tab.key] }}</span>
          </button>
        </div>
        <span class="text-[12px] tabular-nums text-slate-400">{{ rangeText }}</span>
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div v-if="store.loading && !store.ready" class="py-16 text-center text-sm text-slate-400">加载中…</div>

        <EmptyState
          v-else-if="store.items.length === 0"
          :title="emptyState.title"
          :description="emptyState.description"
        >
          <RouterLink v-if="emptyState.action === 'browse'" to="/problems">
            <BaseButton size="sm" variant="primary">{{ emptyState.actionLabel }}</BaseButton>
          </RouterLink>
          <BaseButton v-else size="sm" variant="secondary" @click="switchTab(emptyState.action)">
            {{ emptyState.actionLabel }}
          </BaseButton>
        </EmptyState>

        <div v-else class="divide-y divide-slate-100">
          <ProblemRow
            v-for="p in store.items"
            :key="p.id"
            :problem="p"
            :busy="busyId === p.id"
            remove-title="移出题单"
            @solve="solve"
            @remove="askRemove"
          />
        </div>
      </div>

      <div v-if="store.totalPages > 1" class="mt-4 flex items-center justify-center gap-2">
        <BaseButton size="sm" variant="secondary" :disabled="store.page <= 1" @click="prevPage">上一页</BaseButton>
        <span class="px-2 text-[13px] tabular-nums text-slate-500">{{ store.page }} / {{ store.totalPages }}</span>
        <BaseButton
          size="sm"
          variant="secondary"
          :disabled="store.page >= store.totalPages"
          @click="nextPage"
        >
          下一页
        </BaseButton>
      </div>
    </template>

    <BaseModal :open="Boolean(pendingRemove)" title="移出题单" width="sm" @close="pendingRemove = null">
      <p class="flex items-start gap-2 text-sm text-slate-600">
        <ListX class="mt-0.5 size-4 shrink-0 text-slate-400" />
        <span>
          把「{{ pendingRemove?.title }}」移出本题单？题目本身会保留在题库里，不会被删除。
        </span>
      </p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="pendingRemove = null">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="removing" @click="confirmRemove">移出</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>
