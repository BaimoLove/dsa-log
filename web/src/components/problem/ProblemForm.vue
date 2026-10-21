<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import BaseButton from '@/components/common/BaseButton.vue'
import TagChip from './TagChip.vue'
import { DIFFICULTIES, DIFFICULTY_LABEL, type Difficulty } from '@/types/enums'
import type { Problem } from '@/types/models'
import type { CreateProblemInput } from '@/types/repository'
import { useTagStore } from '@/stores/tag'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'

const props = withDefaults(defineProps<{ initial?: Problem | null; submitting?: boolean }>(), {
  initial: null,
  submitting: false,
})

const emit = defineEmits<{ submit: [payload: CreateProblemInput]; cancel: [] }>()

const tagStore = useTagStore()
const ui = useUiStore()

const form = reactive({
  title: props.initial?.title ?? '',
  difficulty: (props.initial?.difficulty ?? 'medium') as Difficulty,
  source: props.initial?.source ?? '',
  url: props.initial?.url ?? '',
  approach: props.initial?.approach ?? '',
  note: props.initial?.note ?? '',
  tagIds: [...(props.initial?.tagIds ?? [])] as string[],
})

const errors = reactive<{ title?: string; url?: string }>({})
const newTagName = ref('')

watch(
  () => props.initial,
  (p) => {
    form.title = p?.title ?? ''
    form.difficulty = p?.difficulty ?? 'medium'
    form.source = p?.source ?? ''
    form.url = p?.url ?? ''
    form.approach = p?.approach ?? ''
    form.note = p?.note ?? ''
    form.tagIds = [...(p?.tagIds ?? [])]
  },
)

const snapshot = computed(() =>
  JSON.stringify([form.title, form.difficulty, form.source, form.url, form.approach, form.note, form.tagIds]),
)
let baseline = snapshot.value
const isDirty = computed(() => snapshot.value !== baseline)

function validate(): boolean {
  errors.title = form.title.trim() ? undefined : '标题必填'
  errors.url = !form.url.trim() || /^https?:\/\/\S+$/i.test(form.url.trim())
    ? undefined
    : '链接需以 http(s):// 开头，或留空'
  return !errors.title && !errors.url
}

function submit(): void {
  if (!validate()) return
  emit('submit', {
    title: form.title.trim(),
    difficulty: form.difficulty,
    source: form.source.trim(),
    url: form.url.trim(),
    approach: form.approach,
    note: form.note,
    tagIds: [...form.tagIds],
  })
  baseline = snapshot.value
}

function toggleTag(id: string): void {
  const i = form.tagIds.indexOf(id)
  if (i >= 0) form.tagIds.splice(i, 1)
  else form.tagIds.push(id)
}

async function addTag(): Promise<void> {
  const name = newTagName.value.trim()
  if (!name) return
  try {
    await tagStore.create(name)
    const created = tagStore.tags.find((t) => t.name.toLowerCase() === name.toLowerCase())
    if (created && !form.tagIds.includes(created.id)) form.tagIds.push(created.id)
    newTagName.value = ''
  } catch (e) {
    ui.error(toAppError(e).message)
  }
}

function onKeydown(e: KeyboardEvent): void {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  }
}

onMounted(() => {
  if (!tagStore.ready) void tagStore.load()
})

defineExpose({ isDirty })
</script>

<template>
  <form class="space-y-5" @keydown="onKeydown">
    <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
      <h3 class="text-sm font-medium text-slate-900">基本信息</h3>
      <p class="mt-1 text-[12px] text-slate-400">只记题名和链接，原文放外部链接里，不抄题干</p>

      <div class="mt-4 space-y-4">
        <div>
          <label class="mb-1.5 block text-[13px] font-medium text-slate-700">
            题名 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.title"
            type="text"
            maxlength="255"
            placeholder="例如：两数之和"
            class="h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none transition-colors focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
            :class="errors.title ? 'border-red-400' : ''"
          />
          <p v-if="errors.title" class="mt-1 text-[12px] text-red-500">{{ errors.title }}</p>
        </div>

        <div>
          <span class="mb-1.5 block text-[13px] font-medium text-slate-700">难度</span>
          <div class="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
            <button
              v-for="d in DIFFICULTIES"
              :key="d"
              type="button"
              class="rounded-md px-3 py-1.5 text-[13px] transition-colors"
              :class="
                form.difficulty === d
                  ? 'bg-white font-medium text-slate-900 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700'
              "
              @click="form.difficulty = d"
            >
              {{ DIFFICULTY_LABEL[d] }}
            </button>
          </div>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <div>
            <label class="mb-1.5 block text-[13px] font-medium text-slate-700">来源</label>
            <input
              v-model="form.source"
              type="text"
              maxlength="128"
              placeholder="LeetCode / 牛客 / 笔试"
              class="h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
            />
          </div>
          <div>
            <label class="mb-1.5 block text-[13px] font-medium text-slate-700">题目链接</label>
            <input
              v-model="form.url"
              type="text"
              maxlength="1024"
              placeholder="https://leetcode.cn/problems/..."
              class="h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
              :class="errors.url ? 'border-red-400' : ''"
            />
            <p v-if="errors.url" class="mt-1 text-[12px] text-red-500">{{ errors.url }}</p>
          </div>
        </div>

        <div>
          <span class="mb-1.5 block text-[13px] font-medium text-slate-700">标签</span>
          <div class="flex flex-wrap items-center gap-1.5">
            <TagChip
              v-for="t in tagStore.tags"
              :key="t.id"
              :tag="t"
              interactive
              :active="form.tagIds.includes(t.id)"
              @toggle="toggleTag(t.id)"
            />
            <span v-if="tagStore.tags.length === 0" class="text-[12px] text-slate-400">还没有标签，下面新建一个</span>
          </div>
          <div class="mt-2 flex items-center gap-2">
            <input
              v-model="newTagName"
              type="text"
              maxlength="32"
              placeholder="新建标签"
              class="h-8 w-36 rounded-lg border border-slate-300 px-2.5 text-[13px] outline-none focus:border-blue-500"
              @keydown.enter.prevent="addTag"
            />
            <BaseButton size="sm" variant="ghost" :disabled="!newTagName.trim()" @click="addTag">
              <Plus class="size-3.5" />
              添加
            </BaseButton>
          </div>
        </div>
      </div>
    </section>

    <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
      <h3 class="text-sm font-medium text-slate-900">我的记录</h3>
      <p class="mt-1 text-[12px] text-slate-400">写自己的思路和踩坑，复习时看的是这一段</p>

      <div class="mt-4 space-y-4">
        <div>
          <label class="mb-1.5 block text-[13px] font-medium text-slate-700">解题思路 / 代码</label>
          <textarea
            v-model="form.approach"
            rows="5"
            placeholder="核心思路、复杂度、关键代码片段..."
            class="w-full resize-y rounded-lg border border-slate-300 px-3 py-2 font-mono text-[13px] leading-relaxed outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-[13px] font-medium text-slate-700">复盘备注</label>
          <textarea
            v-model="form.note"
            rows="3"
            placeholder="哪里卡住了、下次注意什么..."
            class="w-full resize-y rounded-lg border border-slate-300 px-3 py-2 text-[13px] leading-relaxed outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
          />
        </div>
      </div>
    </section>

    <div class="flex items-center justify-between gap-3">
      <p class="text-[12px] text-slate-400">Ctrl / Cmd + Enter 保存</p>
      <div class="flex items-center gap-2">
        <BaseButton variant="secondary" size="md" @click="emit('cancel')">取消</BaseButton>
        <BaseButton variant="primary" size="md" :loading="submitting" @click="submit">保存</BaseButton>
      </div>
    </div>
  </form>
</template>
