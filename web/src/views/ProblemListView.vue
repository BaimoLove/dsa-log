<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Dices, Plus } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProblemRow from '@/components/problem/ProblemRow.vue'
import { useProblemStore } from '@/stores/problem'
import { useRandomStore } from '@/stores/random'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'
import type { ProblemStatus } from '@/types/enums'

const store = useProblemStore()
const random = useRandomStore()
const ui = useUiStore()

const busyId = ref<string | null>(null)
const removing = ref(false)
const pendingDelete = ref<Problem | null>(null)

const TABS: { key: string; label: string; statuses: ProblemStatus[] }[] = [
  { key: 'all', label: '全部', statuses: [] },
  { key: 'todo', label: '未刷', statuses: ['todo'] },
  { key: 'solved', label: '已刷', statuses: ['solved'] },
]

const activeTab = ref('all')

function switchTab(key: string): void {
  const tab = TABS.find((t) => t.key === key)
  if (!tab) return
  activeTab.value = key
  store.setStatuses(tab.statuses)
  void store.load()
}

function prevPage(): void {
  store.setPage(store.page - 1)
  void store.load()
}

function nextPage(): void {
  store.setPage(store.page + 1)
  void store.load()
}

const rangeText = computed(() => {
  if (store.total === 0) return '共 0 道'
  const from = (store.page - 1) * store.size + 1
  const to = Math.min(store.total, store.page * store.size)
  return `第 ${from}–${to} 道 / 共 ${store.total} 道`
})

onMounted(async () => {
  await store.load()
})

async function solve(id: string): Promise<void> {
  busyId.value = id
  try {
    const updated = await store.markSolved(id)
    ui.success(`已标记刷过（累计 ${updated.solveCount} 次）`)
    // 当前在「未刷」筛选下时，这一条应立刻从列表消失
    if (store.query.statuses?.length) await store.load()
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busyId.value = null
  }
}

function askRemove(p: Problem): void {
  pendingDelete.value = p
}

async function confirmRemove(): Promise<void> {
  if (!pendingDelete.value) return
  removing.value = true
  try {
    await store.removeProblem(pendingDelete.value.id)
    ui.success('已删除')
    pendingDelete.value = null
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader title="题库" description="只记题名和链接，原文去外链看；刷过就点一下">
      <template #actions>
        <BaseButton size="sm" variant="secondary" @click="random.openModal()">
          <Dices class="size-4" />
          随机一题
        </BaseButton>
        <RouterLink to="/problems/new">
          <BaseButton variant="primary" size="sm">
            <Plus class="size-4" />
            新建题目
          </BaseButton>
        </RouterLink>
      </template>
    </PageHeader>

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
        </button>
      </div>
      <span class="text-[12px] text-slate-400">{{ rangeText }}</span>
    </div>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div v-if="store.loading && !store.ready" class="py-16 text-center text-sm text-slate-400">加载中…</div>

      <EmptyState
        v-else-if="store.items.length === 0"
        :title="store.total === 0 ? '还没有题目' : '这个筛选下没有题目'"
        :description="store.total === 0 ? '先录一道题，随手记下题名、难度和标签就够了' : '换个筛选条件试试'"
      >
        <RouterLink v-if="store.total === 0" to="/problems/new">
          <BaseButton variant="primary" size="sm">新建第一道题</BaseButton>
        </RouterLink>
        <BaseButton v-else size="sm" variant="secondary" @click="switchTab('all')">看全部</BaseButton>
      </EmptyState>

      <div v-else class="divide-y divide-slate-100">
        <ProblemRow
          v-for="p in store.items"
          :key="p.id"
          :problem="p"
          :busy="busyId === p.id"
          @solve="solve"
          @remove="askRemove"
        />
      </div>
    </div>

    <div v-if="store.totalPages > 1" class="mt-4 flex items-center justify-center gap-2">
      <BaseButton size="sm" variant="secondary" :disabled="store.page <= 1" @click="prevPage">上一页</BaseButton>
      <span class="px-2 text-[13px] tabular-nums text-slate-500">{{ store.page }} / {{ store.totalPages }}</span>
      <BaseButton size="sm" variant="secondary" :disabled="store.page >= store.totalPages" @click="nextPage">
        下一页
      </BaseButton>
    </div>

    <BaseModal :open="Boolean(pendingDelete)" title="删除题目" width="sm" @close="pendingDelete = null">
      <p class="text-sm text-slate-600">
        确定删除「{{ pendingDelete?.title }}」？该题的复习流水与题单关联会一并清除，不可恢复。
      </p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="pendingDelete = null">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="removing" @click="confirmRemove">删除</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>
