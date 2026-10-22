<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Hash, Pencil, Plus, Search, Trash2, X } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useTagStore } from '@/stores/tag'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { TagWithUsage } from '@/types/repository'

/**
 * 标签管理。
 *
 * 两个既定事实决定了这个页面的形态：
 * 1. 标签详情不落库 —— 题目表里只存 tagIds，标签名与颜色读取时现拼。
 *    所以这里改名/改色之后，题库列表、详情、筛选器立刻就是新值，不需要「批量刷新题目」。
 * 2. 删除是解绑不是级联 —— 后端删标签时摘掉 problem_tag 关联，题目本身不动。
 *    因此删除确认里必须写清「会影响多少道题」，否则用户不敢按。
 */
const DEFAULT_COLOR = '#6366f1'
const MAX_NAME = 32

/** 与题单页面同一套色板，外加两个区分度更高的 */
const TAG_COLORS = [
  '#6366f1',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#ec4899',
  '#64748b',
] as const

const tagStore = useTagStore()
const ui = useUiStore()

const keyword = ref('')
const formOpen = ref(false)
const saving = ref(false)
const form = ref({ id: '', name: '', color: DEFAULT_COLOR as string })

const pendingDelete = ref<TagWithUsage | null>(null)
const deleting = ref(false)

const isEdit = computed(() => Boolean(form.value.id))

/**
 * 本地过滤而不是每次输入都打接口：标签最多几十个，
 * 一屏之外的数据都已经在内存里，本地筛没有延迟，也省掉抖动与竞态处理。
 */
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return tagStore.tags
  return tagStore.tags.filter((t) => t.name.toLowerCase().includes(k))
})

const totalUsage = computed(() => tagStore.tags.reduce((sum, t) => sum + t.usageCount, 0))
const unusedCount = computed(() => tagStore.tags.filter((t) => t.usageCount === 0).length)

onMounted(async () => {
  if (!tagStore.ready) await tagStore.load()
})

function openCreate(): void {
  form.value = { id: '', name: '', color: DEFAULT_COLOR }
  formOpen.value = true
}

function openEdit(tag: TagWithUsage): void {
  form.value = { id: tag.id, name: tag.name, color: tag.color || DEFAULT_COLOR }
  formOpen.value = true
}

async function save(): Promise<void> {
  const name = form.value.name.trim()
  if (!name || saving.value) return
  saving.value = true
  try {
    if (isEdit.value) {
      await tagStore.update(form.value.id, { name, color: form.value.color })
      ui.success('已保存，全站立即生效')
    } else {
      await tagStore.create(name, form.value.color)
      ui.success('已创建')
    }
    formOpen.value = false
  } catch (e) {
    // 重名会从后端返回 409，message 已经写明是哪个名字，直接透出即可
    ui.error(toAppError(e).message)
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value || deleting.value) return
  deleting.value = true
  try {
    await tagStore.remove(pendingDelete.value.id)
    ui.success('已删除')
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
    <PageHeader title="标签" description="改名与改色全站即时生效；删除只解绑，不影响题目本身">
      <template #actions>
        <BaseButton variant="primary" size="sm" @click="openCreate">
          <Plus class="size-4" />
          新建标签
        </BaseButton>
      </template>
    </PageHeader>

    <!-- 汇总：一眼看出「有多少标签、有多少根本没在用」 -->
    <div
      v-if="tagStore.tags.length > 0"
      class="mb-4 flex flex-wrap items-center gap-x-5 gap-y-1.5 rounded-xl border border-slate-200 bg-white px-4 py-3.5 text-[13px]"
    >
      <span class="inline-flex items-center gap-1.5 text-slate-700">
        <Hash class="size-3.5 text-slate-400" />
        <span class="font-medium tabular-nums">{{ tagStore.tags.length }}</span> 个标签
      </span>
      <span class="text-slate-500">
        共被 <span class="font-medium tabular-nums text-slate-700">{{ totalUsage }}</span> 次引用
      </span>
      <span v-if="unusedCount > 0" class="text-amber-600">
        <span class="font-medium tabular-nums">{{ unusedCount }}</span> 个未被使用
      </span>
    </div>

    <!-- 搜索 -->
    <div v-if="tagStore.tags.length > 0" class="mb-3">
      <div class="relative max-w-xs">
        <Search class="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
        <input
          v-model="keyword"
          type="text"
          placeholder="按名称筛选"
          class="h-8 w-full rounded-lg border border-slate-300 bg-white pl-8 pr-8 text-[13px] text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          aria-label="按名称筛选标签"
        />
        <button
          v-if="keyword"
          type="button"
          class="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          aria-label="清空筛选"
          @click="keyword = ''"
        >
          <X class="size-3.5" />
        </button>
      </div>
    </div>

    <!-- 没有任何标签 -->
    <div
      v-if="tagStore.tags.length === 0 && tagStore.ready"
      class="rounded-xl border border-slate-200 bg-white"
    >
      <EmptyState
        title="还没有标签"
        description="标签用来横向切分题目，比如「DP」「图论」「双指针」。一道题可以有多个标签。"
      >
        <BaseButton variant="primary" size="sm" @click="openCreate">新建第一个标签</BaseButton>
      </EmptyState>
    </div>

    <!-- 筛选后为空 -->
    <div
      v-else-if="filtered.length === 0"
      class="rounded-xl border border-slate-200 bg-white"
    >
      <EmptyState title="没有匹配的标签" :description="`没有名称包含「${keyword.trim()}」的标签`">
        <BaseButton size="sm" variant="secondary" @click="keyword = ''">清空筛选</BaseButton>
      </EmptyState>
    </div>

    <div v-else class="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="t in filtered"
        :key="t.id"
        class="group flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-3.5 py-3 transition-shadow hover:shadow-sm"
      >
        <span
          class="size-2.5 shrink-0 rounded-full ring-2 ring-offset-1"
          :style="{ backgroundColor: t.color || DEFAULT_COLOR, '--tw-ring-color': `${t.color || DEFAULT_COLOR}30` }"
        />

        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-slate-900" :title="t.name">{{ t.name }}</p>
          <p class="mt-0.5 text-[12px]" :class="t.usageCount > 0 ? 'text-slate-400' : 'text-amber-600/80'">
            {{ t.usageCount > 0 ? `${t.usageCount} 道题在用` : '暂未使用' }}
          </p>
        </div>

        <!-- 触屏没有 hover，小屏常驻；桌面悬停/聚焦时出现 -->
        <div
          class="flex shrink-0 items-center gap-0.5 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100 sm:focus-within:opacity-100"
        >
          <button
            type="button"
            class="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            title="编辑"
            @click="openEdit(t)"
          >
            <Pencil class="size-3.5" />
          </button>
          <button
            type="button"
            class="rounded-md p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"
            title="删除"
            @click="pendingDelete = t"
          >
            <Trash2 class="size-3.5" />
          </button>
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <BaseModal
      :open="formOpen"
      :title="isEdit ? '编辑标签' : '新建标签'"
      width="sm"
      @close="formOpen = false"
    >
      <div class="space-y-4">
        <label class="block">
          <span class="text-[13px] font-medium text-slate-700">名称</span>
          <input
            v-model="form.name"
            type="text"
            :maxlength="MAX_NAME"
            placeholder="例如：动态规划"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            @keyup.enter="save"
          />
          <span class="mt-1 block text-right text-[11px] tabular-nums text-slate-400">
            {{ form.name.trim().length }} / {{ MAX_NAME }}
          </span>
        </label>

        <div>
          <span class="text-[13px] font-medium text-slate-700">颜色</span>
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <button
              v-for="c in TAG_COLORS"
              :key="c"
              type="button"
              class="size-6 rounded-full ring-offset-2 transition-all"
              :class="form.color.toLowerCase() === c ? 'ring-2 ring-slate-400' : 'hover:scale-110'"
              :style="{ backgroundColor: c }"
              :aria-label="`颜色 ${c}`"
              @click="form.color = c"
            />
            <!-- 预设不够用时自己取色；浏览器原生取色器，不用引第三方组件 -->
            <label
              class="relative inline-flex size-6 cursor-pointer items-center justify-center rounded-full border border-dashed border-slate-300 text-slate-400 hover:border-slate-400 hover:text-slate-600"
              title="自定义颜色"
            >
              <Plus class="size-3" />
              <input v-model="form.color" type="color" class="absolute inset-0 cursor-pointer opacity-0" />
            </label>
          </div>

          <!-- 实时预览：确认这个颜色配着名字好不好看 -->
          <div class="mt-3 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
            <span class="text-[11px] text-slate-400">预览</span>
            <div class="mt-1">
              <span
                class="inline-flex max-w-full items-center rounded-md border px-1.5 py-0.5 text-[11px] font-medium"
                :style="{
                  color: form.color || DEFAULT_COLOR,
                  backgroundColor: `${form.color || DEFAULT_COLOR}12`,
                  borderColor: `${form.color || DEFAULT_COLOR}33`,
                }"
              >
                <span class="truncate">{{ form.name.trim() || '标签名' }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="formOpen = false">取消</BaseButton>
        <BaseButton
          size="sm"
          variant="primary"
          :loading="saving"
          :disabled="!form.name.trim()"
          @click="save"
        >
          {{ isEdit ? '保存' : '创建' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- 删除确认 -->
    <BaseModal
      :open="Boolean(pendingDelete)"
      title="删除标签"
      width="sm"
      @close="pendingDelete = null"
    >
      <div v-if="pendingDelete" class="space-y-2.5 text-[13px] leading-relaxed">
        <p class="text-slate-700">
          确定删除标签
          <span class="font-medium" :style="{ color: pendingDelete.color || DEFAULT_COLOR }">
            「{{ pendingDelete.name }}」
          </span>
          吗？
        </p>
        <p v-if="pendingDelete.usageCount > 0" class="rounded-lg bg-amber-50 px-3 py-2 text-amber-800">
          这个标签正被
          <span class="font-medium tabular-nums">{{ pendingDelete.usageCount }}</span>
          道题使用。删除后会从这些题上解绑，<span class="font-medium">题目本身不会删除</span>。
        </p>
        <p v-else class="text-slate-500">这个标签还没有被任何题使用。</p>
        <p class="text-slate-400">此操作不可撤销。</p>
      </div>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="pendingDelete = null">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="deleting" @click="confirmDelete">删除</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>
