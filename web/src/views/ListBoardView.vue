<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { FolderPlus, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useListStore } from '@/stores/list'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { ListProgress } from '@/types/repository'

/**
 * 题单看板（M3 版）：CRUD + 进度条。
 * 进度环留到 M5，届时换成一个 SVG 环，其余结构不动。
 */
const LIST_COLORS = ['#6366f1', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'] as const

const listStore = useListStore()
const ui = useUiStore()

const formOpen = ref(false)
const saving = ref(false)
const form = ref({ id: '', name: '', description: '', color: LIST_COLORS[0] as string })

const pendingDelete = ref<ListProgress | null>(null)
const deleting = ref(false)

const isEdit = computed(() => Boolean(form.value.id))
const overall = computed(() => {
  const solved = listStore.totalSolved
  const total = listStore.totalItems
  return { solved, total, percent: percent(solved, total) }
})

function percent(solved: number, total: number): number {
  if (!total) return 0
  return Math.round((solved / total) * 100)
}

onMounted(async () => {
  await listStore.load()
})

function openCreate(): void {
  form.value = { id: '', name: '', description: '', color: LIST_COLORS[0] }
  formOpen.value = true
}

function openEdit(list: ListProgress): void {
  form.value = {
    id: list.id,
    name: list.name,
    description: list.description,
    color: list.color || LIST_COLORS[0],
  }
  formOpen.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    if (isEdit.value) {
      await listStore.update(form.value.id, {
        name: form.value.name,
        description: form.value.description,
        color: form.value.color,
      })
      ui.success('已保存')
    } else {
      await listStore.create({
        name: form.value.name,
        description: form.value.description,
        color: form.value.color,
      })
      ui.success('已创建')
    }
    formOpen.value = false
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) return
  deleting.value = true
  try {
    await listStore.remove(pendingDelete.value.id)
    ui.success('已删除题单')
    pendingDelete.value = null
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader title="题单" description="按专题分组，一题可属于多个题单">
      <template #actions>
        <BaseButton variant="primary" size="sm" @click="openCreate">
          <Plus class="size-4" />
          新建题单
        </BaseButton>
      </template>
    </PageHeader>

    <div v-if="listStore.lists.length > 0" class="mb-3 flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
      <span class="text-[13px] text-slate-500">全部题单合计</span>
      <div class="h-1.5 flex-1 overflow-hidden rounded bg-slate-100">
        <div class="h-full rounded bg-blue-500 transition-[width]" :style="{ width: `${overall.percent}%` }" />
      </div>
      <span class="text-[12px] tabular-nums text-slate-400">
        {{ overall.solved }} / {{ overall.total }}（{{ overall.percent }}%）
      </span>
    </div>

    <div v-if="listStore.lists.length === 0 && listStore.ready" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="还没有题单" description="题单用来把同一专题的题归到一起，比如「动态规划 30 题」">
        <BaseButton variant="primary" size="sm" @click="openCreate">新建第一个题单</BaseButton>
      </EmptyState>
    </div>

    <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="l in listStore.lists"
        :key="l.id"
        class="group rounded-xl border border-slate-200 bg-white p-4 transition-shadow hover:shadow-sm"
      >
        <div class="flex items-start gap-2">
          <span class="mt-1.5 size-2.5 shrink-0 rounded-full" :style="{ backgroundColor: l.color }" />
          <RouterLink :to="`/lists/${l.id}`" class="min-w-0 flex-1 truncate text-sm font-medium text-slate-900 hover:text-blue-600">
            {{ l.name }}
          </RouterLink>
          <div class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              type="button"
              class="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              title="编辑"
              @click="openEdit(l)"
            >
              <Pencil class="size-3.5" />
            </button>
            <button
              type="button"
              class="rounded-md p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
              title="删除"
              @click="pendingDelete = l"
            >
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </div>

        <p class="mt-1.5 line-clamp-2 min-h-[2.2em] text-[12px] leading-relaxed text-slate-400">
          {{ l.description || '暂无说明' }}
        </p>

        <div class="mt-3">
          <div class="flex items-center justify-between text-[12px]">
            <span class="text-slate-500">已刷 {{ l.solved }} / {{ l.total }}</span>
            <span class="tabular-nums text-slate-400">{{ percent(l.solved, l.total) }}%</span>
          </div>
          <div class="mt-1.5 h-1.5 overflow-hidden rounded bg-slate-100">
            <div
              class="h-full rounded bg-blue-500 transition-[width]"
              :style="{ width: `${percent(l.solved, l.total)}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <BaseModal :open="formOpen" :title="isEdit ? '编辑题单' : '新建题单'" width="sm" @close="formOpen = false">
      <div class="space-y-3">
        <label class="block">
          <span class="text-[13px] font-medium text-slate-700">名称</span>
          <input
            v-model="form.name"
            type="text"
            maxlength="64"
            placeholder="例如：动态规划 30 题"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          />
        </label>
        <label class="block">
          <span class="text-[13px] font-medium text-slate-700">说明（可选）</span>
          <textarea
            v-model="form.description"
            rows="2"
            maxlength="512"
            placeholder="这个题单要解决什么"
            class="mt-1 w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          />
        </label>
        <div>
          <span class="text-[13px] font-medium text-slate-700">颜色</span>
          <div class="mt-1.5 flex gap-2">
            <button
              v-for="c in LIST_COLORS"
              :key="c"
              type="button"
              class="size-6 rounded-full ring-offset-2 transition-all"
              :class="form.color === c ? 'ring-2 ring-slate-400' : ''"
              :style="{ backgroundColor: c }"
              :aria-label="`颜色 ${c}`"
              @click="form.color = c"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="formOpen = false">取消</BaseButton>
        <BaseButton size="sm" variant="primary" :loading="saving" :disabled="!form.name.trim()" @click="save">
          {{ isEdit ? '保存' : '创建' }}
        </BaseButton>
      </template>
    </BaseModal>

    <BaseModal :open="Boolean(pendingDelete)" title="删除题单" width="sm" @close="pendingDelete = null">
      <p class="text-sm text-slate-600">
        确定删除题单「{{ pendingDelete?.name }}」？题单内的题目本身不会被删除，只是不再属于这个题单。
      </p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="pendingDelete = null">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="deleting" @click="confirmDelete">删除</BaseButton>
      </template>
    </BaseModal>

    <p v-if="listStore.lists.length > 0" class="mt-4 flex items-center gap-1.5 text-[12px] text-slate-400">
      <FolderPlus class="size-3.5" />
      在题库页勾选题目后可批量加入题单
    </p>
  </div>
</template>
