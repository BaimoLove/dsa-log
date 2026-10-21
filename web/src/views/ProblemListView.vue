<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CheckCheck, Dices, FolderPlus, Plus, Tags, Trash2, X } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProblemRow from '@/components/problem/ProblemRow.vue'
import { useProblemStore } from '@/stores/problem'
import { useRandomStore } from '@/stores/random'
import { useTagStore } from '@/stores/tag'
import { useListStore } from '@/stores/list'
import { useUiStore } from '@/stores/ui'
import { useUndoStore } from '@/stores/undo'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'
import type { BatchResult } from '@/types/repository'
import type { ProblemStatus } from '@/types/enums'

const store = useProblemStore()
const random = useRandomStore()
const tagStore = useTagStore()
const listStore = useListStore()
const ui = useUiStore()
const undo = useUndoStore()

const busyId = ref<string | null>(null)
const removing = ref(false)
const pendingDelete = ref<Problem | null>(null)

const TABS: { key: string; label: string; statuses: ProblemStatus[] }[] = [
  { key: 'all', label: '全部', statuses: [] },
  { key: 'todo', label: '未刷', statuses: ['todo'] },
  { key: 'solved', label: '已刷', statuses: ['solved'] },
]

const activeTab = ref('all')

/* ---------------- 批量选择 ---------------- */

// 用替换而不是原地 mutate：ref 里存 Set 时，原地 add/delete 的依赖追踪不如整块替换直观
const selected = ref<Set<string>>(new Set())
const selectedCount = computed(() => selected.value.size)
const selectedIds = computed(() => Array.from(selected.value))
const allSelected = computed(
  () => store.items.length > 0 && store.items.every((p) => selected.value.has(p.id)),
)

function isSelected(id: string): boolean {
  return selected.value.has(id)
}

function toggleOne(id: string): void {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function toggleAll(): void {
  selected.value = allSelected.value ? new Set() : new Set(store.items.map((p) => p.id))
}

function clearSelection(): void {
  selected.value = new Set()
}

/* ---------------- 批量操作 ---------------- */

const bulkBusy = ref(false)
const tagPickerOpen = ref(false)
const listPickerOpen = ref(false)
const bulkDeleteOpen = ref(false)
const pickedTagIds = ref<string[]>([])
const pickedListId = ref('')

/** 批量收尾：清空选择 + 弹撤销条，撤销成功后重拉当前页 */
function afterBulk(res: BatchResult, label: string): void {
  clearSelection()
  if (res.affected === 0) {
    ui.toast('没有实际变更（可能已符合目标状态）', 'info')
    return
  }
  undo.offer(res, `${label}（${res.affected} 道）`, () => store.load())
}

async function runBulk(task: () => Promise<BatchResult>, label: string): Promise<void> {
  if (!selectedCount.value || bulkBusy.value) return
  bulkBusy.value = true
  try {
    afterBulk(await task(), label)
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    bulkBusy.value = false
  }
}

function bulkSolve(): void {
  void runBulk(() => store.bulkMarkSolved(selectedIds.value), '已标记为已刷')
}

function openTagPicker(): void {
  pickedTagIds.value = []
  tagPickerOpen.value = true
}

async function confirmAddTags(): Promise<void> {
  if (!pickedTagIds.value.length) return
  await runBulk(
    () => store.bulkAddTags(selectedIds.value, pickedTagIds.value),
    `已打上 ${pickedTagIds.value.length} 个标签`,
  )
  tagPickerOpen.value = false
}

function openListPicker(): void {
  pickedListId.value = listStore.lists[0]?.id ?? ''
  listPickerOpen.value = true
}

async function confirmAddToList(): Promise<void> {
  if (!pickedListId.value) return
  await runBulk(() => store.bulkAddToList(selectedIds.value, pickedListId.value), '已加入题单')
  listPickerOpen.value = false
}

async function confirmBulkRemove(): Promise<void> {
  await runBulk(() => store.bulkRemove(selectedIds.value), '已删除')
  bulkDeleteOpen.value = false
}

/* ---------------- 单条操作 ---------------- */

function switchTab(key: string): void {
  const tab = TABS.find((t) => t.key === key)
  if (!tab) return
  activeTab.value = key
  clearSelection()
  store.setStatuses(tab.statuses)
  void store.load()
}

function prevPage(): void {
  clearSelection()
  store.setPage(store.page - 1)
  void store.load()
}

function nextPage(): void {
  clearSelection()
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
  clearSelection()
  // 从题单详情页返回时可能残留 listId 筛选，这里复位
  store.setListId(undefined)
  await Promise.all([store.load(), tagStore.load(), listStore.load()])
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

    <!-- 选中后浮现的操作条：只放批量动作，避免和单条按钮挤在一起 -->
    <div
      v-if="selectedCount > 0"
      class="mb-3 flex flex-wrap items-center gap-2 rounded-xl border border-blue-200 bg-blue-50/80 px-3 py-2"
    >
      <span class="text-[13px] font-medium text-blue-900">已选 {{ selectedCount }} 道</span>
      <BaseButton size="sm" variant="secondary" :disabled="bulkBusy" @click="bulkSolve">
        <CheckCheck class="size-3.5" />
        标记已刷
      </BaseButton>
      <BaseButton size="sm" variant="secondary" :disabled="bulkBusy" @click="openTagPicker">
        <Tags class="size-3.5" />
        打标签
      </BaseButton>
      <BaseButton size="sm" variant="secondary" :disabled="bulkBusy" @click="openListPicker">
        <FolderPlus class="size-3.5" />
        加入题单
      </BaseButton>
      <BaseButton size="sm" variant="danger" :disabled="bulkBusy" @click="bulkDeleteOpen = true">
        <Trash2 class="size-3.5" />
        删除
      </BaseButton>
      <button
        type="button"
        class="ml-auto inline-flex items-center gap-1 rounded-md px-2 py-1 text-[12px] text-blue-700 hover:bg-blue-100"
        @click="clearSelection"
      >
        <X class="size-3.5" />
        取消选择
      </button>
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

      <template v-else>
        <label class="flex cursor-pointer items-center gap-2 border-b border-slate-100 bg-slate-50/70 px-4 py-2">
          <input
            type="checkbox"
            class="size-4 cursor-pointer rounded border-slate-300 accent-blue-600"
            :checked="allSelected"
            @change="toggleAll"
          />
          <span class="text-[12px] text-slate-500">
            {{ allSelected ? '取消全选本页' : `全选本页（${store.items.length} 道）` }}
          </span>
        </label>

        <div class="divide-y divide-slate-100">
          <ProblemRow
            v-for="p in store.items"
            :key="p.id"
            :problem="p"
            :busy="busyId === p.id"
            selectable
            :selected="isSelected(p.id)"
            @solve="solve"
            @remove="askRemove"
            @toggle="toggleOne"
          />
        </div>
      </template>
    </div>

    <div v-if="store.totalPages > 1" class="mt-4 flex items-center justify-center gap-2">
      <BaseButton size="sm" variant="secondary" :disabled="store.page <= 1" @click="prevPage">上一页</BaseButton>
      <span class="px-2 text-[13px] tabular-nums text-slate-500">{{ store.page }} / {{ store.totalPages }}</span>
      <BaseButton size="sm" variant="secondary" :disabled="store.page >= store.totalPages" @click="nextPage">
        下一页
      </BaseButton>
    </div>

    <!-- 单条删除 -->
    <BaseModal :open="Boolean(pendingDelete)" title="删除题目" width="sm" @close="pendingDelete = null">
      <p class="text-sm text-slate-600">
        确定删除「{{ pendingDelete?.title }}」？该题的复习流水与题单关联会一并清除，不可恢复。
      </p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="pendingDelete = null">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="removing" @click="confirmRemove">删除</BaseButton>
      </template>
    </BaseModal>

    <!-- 批量打标签 -->
    <BaseModal :open="tagPickerOpen" title="批量打标签" width="sm" @close="tagPickerOpen = false">
      <p class="mb-3 text-[13px] text-slate-500">为选中的 {{ selectedCount }} 道题追加标签（不会覆盖已有标签）</p>
      <div v-if="tagStore.tags.length === 0" class="rounded-lg bg-slate-50 px-3 py-6 text-center text-[13px] text-slate-500">
        还没有标签，
        <RouterLink to="/tags" class="text-blue-600 hover:underline" @click="tagPickerOpen = false">去标签页新建</RouterLink>
      </div>
      <div v-else class="flex flex-wrap gap-2">
        <label
          v-for="t in tagStore.tags"
          :key="t.id"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-[13px] transition-colors"
          :class="
            pickedTagIds.includes(t.id)
              ? 'border-blue-300 bg-blue-50 text-blue-800'
              : 'border-slate-200 text-slate-600 hover:bg-slate-50'
          "
        >
          <input v-model="pickedTagIds" type="checkbox" class="size-3.5 accent-blue-600" :value="t.id" />
          <span class="size-2 rounded-full" :style="{ backgroundColor: t.color }" />
          {{ t.name }}
        </label>
      </div>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="tagPickerOpen = false">取消</BaseButton>
        <BaseButton
          size="sm"
          variant="primary"
          :disabled="!pickedTagIds.length || bulkBusy"
          :loading="bulkBusy"
          @click="confirmAddTags"
        >
          确定
        </BaseButton>
      </template>
    </BaseModal>

    <!-- 批量加入题单 -->
    <BaseModal :open="listPickerOpen" title="加入题单" width="sm" @close="listPickerOpen = false">
      <p class="mb-3 text-[13px] text-slate-500">把选中的 {{ selectedCount }} 道题加入：</p>
      <div v-if="listStore.lists.length === 0" class="rounded-lg bg-slate-50 px-3 py-6 text-center text-[13px] text-slate-500">
        还没有题单，
        <RouterLink to="/lists" class="text-blue-600 hover:underline" @click="listPickerOpen = false">去题单页新建</RouterLink>
      </div>
      <div v-else class="space-y-1.5">
        <label
          v-for="l in listStore.lists"
          :key="l.id"
          class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-[13px] transition-colors"
          :class="
            pickedListId === l.id ? 'border-blue-300 bg-blue-50 text-blue-900' : 'border-slate-200 hover:bg-slate-50'
          "
        >
          <input v-model="pickedListId" type="radio" class="size-3.5 accent-blue-600" :value="l.id" />
          <span class="flex-1 truncate font-medium">{{ l.name }}</span>
          <span class="shrink-0 text-[12px] tabular-nums text-slate-400">{{ l.solved }}/{{ l.total }}</span>
        </label>
      </div>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="listPickerOpen = false">取消</BaseButton>
        <BaseButton
          size="sm"
          variant="primary"
          :disabled="!pickedListId || bulkBusy"
          :loading="bulkBusy"
          @click="confirmAddToList"
        >
          加入
        </BaseButton>
      </template>
    </BaseModal>

    <!-- 批量删除 -->
    <BaseModal :open="bulkDeleteOpen" title="批量删除" width="sm" @close="bulkDeleteOpen = false">
      <p class="text-sm text-slate-600">
        确定删除选中的 {{ selectedCount }} 道题？复习流水与题单关联会一并清除。
      </p>
      <p class="mt-2 text-[13px] text-slate-500">删除后 6 秒内可以撤销。</p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="bulkDeleteOpen = false">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="bulkBusy" @click="confirmBulkRemove">删除</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>
