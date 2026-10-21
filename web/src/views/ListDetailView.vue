<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ArrowLeft, ListX } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProblemRow from '@/components/problem/ProblemRow.vue'
import { useListStore } from '@/stores/list'
import { useProblemStore } from '@/stores/problem'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'

const route = useRoute()
const listStore = useListStore()
const store = useProblemStore()
const ui = useUiStore()

const listId = computed(() => String(route.params.id ?? ''))
const list = computed(() => listStore.byId(listId.value))

const busyId = ref<string | null>(null)
const pendingRemove = ref<Problem | null>(null)
const removing = ref(false)

const percent = computed(() => {
  const l = list.value
  if (!l || !l.total) return 0
  return Math.round((l.solved / l.total) * 100)
})

async function refresh(): Promise<void> {
  await Promise.all([listStore.load(), store.load()])
}

onMounted(async () => {
  store.setListId(listId.value)
  // 题库页的筛选（关键词/难度/标签）不该带进题单详情，进来看完整题单
  store.resetFilters()
  await refresh()
})

// 题单之间直接跳转时组件会复用，必须重新绑定筛选条件
watch(listId, async (id) => {
  if (!id) return
  store.setListId(id)
  store.resetFilters()
  await refresh()
})

onBeforeUnmount(() => {
  // 复位，否则回到题库页会继续被 listId 过滤
  store.setListId(undefined)
})

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
    await listStore.load()
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busyId.value = null
  }
}

function askRemove(p: Problem): void {
  pendingRemove.value = p
}

async function confirmRemove(): Promise<void> {
  if (!pendingRemove.value) return
  removing.value = true
  try {
    await listStore.removeProblem(listId.value, pendingRemove.value.id)
    ui.success('已移出题单')
    pendingRemove.value = null
    await refresh()
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

    <div v-if="!listStore.ready" class="rounded-xl border border-slate-200 bg-white py-16 text-center text-sm text-slate-400">
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
      <div class="mb-3 flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
        <span class="size-2.5 rounded-full" :style="{ backgroundColor: list.color }" />
        <span class="text-[13px] text-slate-500">进度</span>
        <div class="h-1.5 flex-1 overflow-hidden rounded bg-slate-100">
          <div class="h-full rounded bg-blue-500 transition-[width]" :style="{ width: `${percent}%` }" />
        </div>
        <span class="text-[12px] tabular-nums text-slate-400">
          {{ list.solved }} / {{ list.total }}（{{ percent }}%）
        </span>
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <EmptyState
          v-if="store.items.length === 0"
          title="这个题单还没有题目"
          description="去题库页勾选题目，用「加入题单」批量放进来"
        >
          <RouterLink to="/problems">
            <BaseButton size="sm" variant="primary">去题库挑题</BaseButton>
          </RouterLink>
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
        <BaseButton size="sm" variant="secondary" :disabled="store.page >= store.totalPages" @click="nextPage">
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
