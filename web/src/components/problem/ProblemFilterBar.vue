<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onClickOutside, useTimeoutFn } from '@vueuse/core'
import { ChevronDown, Search, Tags, X } from 'lucide-vue-next'
import { DIFFICULTIES, DIFFICULTY_LABEL, SORT_LABEL, SORT_OPTIONS } from '@/types/enums'
import type { Difficulty, SortOption } from '@/types/enums'
import type { TagBasic } from '@/types/repository'
import { countActiveFilters, DEFAULT_SORT, toggleValue } from '@/utils/problemQuery'
import type { ProblemFilter } from '@/utils/problemQuery'

/**
 * 题库筛选条。自身不持有筛选状态——全部通过 `change` 抛给页面，
 * 页面写进 URL 后再由 store 重新查询，形成单向数据流：
 * 交互 → URL → store → 列表。这样刷新、前进后退、手改地址都自动一致。
 */
const props = defineProps<{
  keyword: string
  difficulties: Difficulty[]
  tagIds: string[]
  sort: SortOption
  tags: TagBasic[]
}>()

const emit = defineEmits<{
  change: [patch: Partial<ProblemFilter>]
  clear: []
}>()

const input = ref<HTMLInputElement | null>(null)
const tagPanel = ref(false)
const tagWrap = ref<HTMLElement | null>(null)

/**
 * 输入框保留本地状态：每敲一个字就写 URL 会打断输入法候选，也会让路由频繁 replace。
 * 250ms 静默后才提交，`stop()` 用于清空时取消挂起的提交。
 */
const keywordInput = ref(props.keyword)
let pendingKeyword = ''

watch(
  () => props.keyword,
  (value) => {
    // 只在「真实变化」时回写本地值：URL 里的关键词是 trim 过的，
    // 直接同步会把用户刚敲的空格吃掉，光标也会跳
    if (value !== keywordInput.value && value !== keywordInput.value.trim()) keywordInput.value = value
  },
)

const { start: scheduleCommit, stop: cancelCommit } = useTimeoutFn(() => {
  if (pendingKeyword.trim() === props.keyword.trim()) return
  emit('change', { keyword: pendingKeyword })
}, 250)

function onInput(): void {
  pendingKeyword = keywordInput.value
  scheduleCommit()
}

function clearKeyword(): void {
  // 取消挂起的提交，否则清空后又被旧值覆盖回来
  cancelCommit()
  keywordInput.value = ''
  emit('change', { keyword: '' })
}

function toggleDifficulty(value: Difficulty): void {
  emit('change', { difficulties: toggleValue(props.difficulties, value) })
}

function toggleTag(id: string): void {
  emit('change', { tagIds: toggleValue(props.tagIds, id) })
}

function onSort(event: Event): void {
  emit('change', { sort: (event.target as HTMLSelectElement).value as SortOption })
}

function reset(): void {
  cancelCommit()
  emit('clear')
}

onClickOutside(tagWrap, () => {
  tagPanel.value = false
})

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    tagPanel.value = false
    input.value?.blur()
  }
}

onBeforeUnmount(() => cancelCommit())

const activeCount = computed(() =>
  countActiveFilters({
    keyword: props.keyword,
    statuses: [],
    difficulties: props.difficulties,
    tagIds: props.tagIds,
    sort: props.sort,
  }),
)

const tagMap = computed(() => new Map(props.tags.map((t) => [t.id, t])))

function focus(): void {
  input.value?.focus()
  input.value?.select()
}

defineExpose({ focus })
</script>

<template>
  <div class="mb-3 space-y-2">
    <div class="flex flex-wrap items-center gap-2">
      <!-- 搜索：Ctrl+K / / 聚焦，见 AppShell 的全局快捷键 -->
      <div class="relative min-w-[200px] flex-1">
        <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
        <input
          id="problem-search"
          ref="input"
          v-model="keywordInput"
          type="search"
          aria-label="搜索题目"
          placeholder="搜索题名 / 思路 / 备注 / 来源"
          class="h-9 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-16 text-[13px] text-slate-800 placeholder:text-slate-400 focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100"
          @input="onInput"
          @keydown="onKeydown"
        />
        <button
          v-if="keywordInput"
          type="button"
          aria-label="清除搜索"
          class="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          @click="clearKeyword"
        >
          <X class="size-3.5" />
        </button>
        <kbd
          v-else
          class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 font-mono text-[10px] text-slate-400"
        >
          Ctrl K
        </kbd>
      </div>

      <!-- 难度多选：并集 -->
      <div class="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
        <button
          v-for="d in DIFFICULTIES"
          :key="d"
          type="button"
          class="rounded-md px-2.5 py-1.5 text-[13px] transition-colors"
          :class="
            difficulties.includes(d)
              ? 'bg-white font-medium text-slate-900 shadow-sm'
              : 'text-slate-500 hover:text-slate-700'
          "
          :aria-pressed="difficulties.includes(d)"
          @click="toggleDifficulty(d)"
        >
          {{ DIFFICULTY_LABEL[d] }}
        </button>
      </div>

      <!-- 标签多选：交集（与后端 HAVING COUNT(DISTINCT) 同口径） -->
      <div ref="tagWrap" class="relative">
        <button
          type="button"
          class="inline-flex h-9 items-center gap-1.5 rounded-lg border px-3 text-[13px] transition-colors"
          :class="
            tagIds.length
              ? 'border-blue-300 bg-blue-50 text-blue-800'
              : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
          "
          :aria-expanded="tagPanel"
          @click="tagPanel = !tagPanel"
        >
          <Tags class="size-3.5" />
          标签
          <span v-if="tagIds.length" class="tabular-nums">· {{ tagIds.length }}</span>
          <ChevronDown class="size-3.5 text-slate-400" />
        </button>

        <div
          v-if="tagPanel"
          class="absolute right-0 z-30 mt-1 w-64 rounded-xl border border-slate-200 bg-white p-2 shadow-lg"
          role="group"
          aria-label="按标签筛选"
        >
          <p class="px-1 pb-1.5 text-[11px] text-slate-400">多选表示「同时命中」，不是「任一命中」</p>
          <div v-if="tags.length === 0" class="px-1 py-4 text-center text-[12px] text-slate-400">
            还没有标签，去标签页建几个
          </div>
          <div v-else class="max-h-64 space-y-0.5 overflow-y-auto">
            <label
              v-for="t in tags"
              :key="t.id"
              class="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-[13px] hover:bg-slate-50"
            >
              <input
                type="checkbox"
                class="size-3.5 cursor-pointer rounded border-slate-300 accent-blue-600"
                :checked="tagIds.includes(t.id)"
                @change="toggleTag(t.id)"
              />
              <span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: t.color }" />
              <span class="truncate text-slate-700">{{ t.name }}</span>
            </label>
          </div>
          <div v-if="tagIds.length" class="mt-1 border-t border-slate-100 pt-1">
            <button
              type="button"
              class="w-full rounded-md px-2 py-1 text-left text-[12px] text-slate-500 hover:bg-slate-50"
              @click="emit('change', { tagIds: [] })"
            >
              清除标签
            </button>
          </div>
        </div>
      </div>

      <select
        :value="sort"
        aria-label="排序方式"
        class="h-9 rounded-lg border border-slate-300 bg-white px-2 text-[13px] text-slate-700 focus:border-blue-400 focus:outline-none"
        @change="onSort"
      >
        <option v-for="s in SORT_OPTIONS" :key="s" :value="s">{{ SORT_LABEL[s] }}</option>
      </select>

      <button
        v-if="activeCount > 0"
        type="button"
        class="h-9 rounded-lg px-2.5 text-[13px] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
        @click="reset"
      >
        清空筛选
      </button>
    </div>

    <!-- 生效中的条件：看得见才敢信任结果，每条都能单独摘掉 -->
    <div v-if="activeCount > 0" class="flex flex-wrap items-center gap-1.5">
      <span class="text-[11px] text-slate-400">生效中</span>

      <span
        v-if="keyword.trim()"
        class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-600"
      >
        关键词「{{ keyword.trim() }}」
        <button type="button" aria-label="移除关键词条件" class="rounded p-0.5 hover:bg-black/5" @click="emit('change', { keyword: '' })">
          <X class="size-3" />
        </button>
      </span>

      <span
        v-for="d in difficulties"
        :key="d"
        class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-600"
      >
        {{ DIFFICULTY_LABEL[d] }}
        <button type="button" aria-label="移除难度条件" class="rounded p-0.5 hover:bg-black/5" @click="toggleDifficulty(d)">
          <X class="size-3" />
        </button>
      </span>

      <span
        v-for="id in tagIds"
        :key="id"
        class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-600"
      >
        #{{ tagMap.get(id)?.name ?? '已删除' }}
        <button type="button" aria-label="移除标签条件" class="rounded p-0.5 hover:bg-black/5" @click="toggleTag(id)">
          <X class="size-3" />
        </button>
      </span>

      <span
        v-if="sort !== DEFAULT_SORT"
        class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-600"
      >
        {{ SORT_LABEL[sort] }}
        <button type="button" aria-label="恢复默认排序" class="rounded p-0.5 hover:bg-black/5" @click="emit('change', { sort: DEFAULT_SORT })">
          <X class="size-3" />
        </button>
      </span>
    </div>
  </div>
</template>
