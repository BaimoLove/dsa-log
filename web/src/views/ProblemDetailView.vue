<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, ExternalLink, Pencil, RotateCcw, Trash2 } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import DifficultyBadge from '@/components/problem/DifficultyBadge.vue'
import TagChip from '@/components/problem/TagChip.vue'
import { useProblemStore } from '@/stores/problem'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'
import { formatDateTime, fromNow } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const store = useProblemStore()
const ui = useUiStore()

const problem = ref<Problem | null>(null)
const loading = ref(true)
const busy = ref(false)
const missing = ref(false)
const confirmDelete = ref(false)

const id = computed(() => route.params.id as string)

const reviewStageText = computed(() => {
  const p = problem.value
  if (!p || p.nextReviewAt === null) return '不在复习池'
  return `第 ${p.reviewStage} 阶 · 下次 ${formatDateTime(p.nextReviewAt)}（${fromNow(p.nextReviewAt)}）`
})

async function load(): Promise<void> {
  loading.value = true
  try {
    problem.value = await store.fetchById(id.value)
    missing.value = !problem.value
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function solve(): Promise<void> {
  if (!problem.value) return
  busy.value = true
  try {
    const updated = await store.markSolved(problem.value.id)
    problem.value = updated
    ui.success(updated.solveCount === 1 ? '已标记刷过，明天会提醒复习' : `又刷了一次（累计 ${updated.solveCount} 次）`)
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busy.value = false
  }
}

async function unsolve(): Promise<void> {
  if (!problem.value) return
  busy.value = true
  try {
    problem.value = await store.markUnsolved(problem.value.id)
    ui.success('已改回未刷，历史记录保留')
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busy.value = false
  }
}

async function remove(): Promise<void> {
  if (!problem.value) return
  busy.value = true
  try {
    await store.removeProblem(problem.value.id)
    ui.success('已删除')
    void router.replace('/problems')
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <button type="button" class="mb-3 inline-flex items-center gap-1 text-[13px] text-slate-500 hover:text-slate-700" @click="router.push('/problems')">
      <ArrowLeft class="size-3.5" />
      回题库
    </button>

    <div v-if="loading" class="rounded-xl border border-slate-200 bg-white py-16 text-center text-sm text-slate-400">
      加载中…
    </div>

    <div v-else-if="missing" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="题目不存在或已删除" description="可能已经被删掉了">
        <BaseButton size="sm" variant="primary" @click="router.replace('/problems')">回题库</BaseButton>
      </EmptyState>
    </div>

    <template v-else-if="problem">
      <PageHeader :title="problem.title" :description="problem.source || '未填来源'">
        <template #actions>
          <RouterLink :to="`/problems/${problem.id}/edit`">
            <BaseButton size="sm" variant="secondary">
              <Pencil class="size-3.5" />
              编辑
            </BaseButton>
          </RouterLink>
          <BaseButton v-if="problem.status === 'todo'" size="sm" variant="primary" :loading="busy" @click="solve">
            <Check class="size-3.5" />
            标记已刷
          </BaseButton>
          <template v-else>
            <BaseButton size="sm" variant="primary" :loading="busy" @click="solve">又刷了一次</BaseButton>
            <BaseButton size="sm" variant="ghost" :disabled="busy" @click="unsolve">
              <RotateCcw class="size-3.5" />
              改回未刷
            </BaseButton>
          </template>
          <BaseButton size="sm" variant="ghost" @click="confirmDelete = true">
            <Trash2 class="size-3.5 text-slate-400" />
          </BaseButton>
        </template>
      </PageHeader>

      <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
        <div class="flex flex-wrap items-center gap-2">
          <DifficultyBadge :difficulty="problem.difficulty" size="md" />
          <span
            class="inline-flex items-center rounded-md px-2 py-1 text-xs font-medium"
            :class="problem.status === 'solved' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-500'"
          >
            {{ problem.status === 'solved' ? '已刷' : '未刷' }}
          </span>
          <TagChip v-for="t in problem.tags" :key="t.id" :tag="t" />
          <a
            v-if="problem.url"
            :href="problem.url"
            target="_blank"
            rel="noopener noreferrer"
            class="ml-auto inline-flex items-center gap-1 text-[12px] font-medium text-blue-600 hover:underline"
          >
            打开原题
            <ExternalLink class="size-3" />
          </a>
        </div>

        <dl class="mt-4 grid gap-3 border-t border-slate-100 pt-4 text-[13px] sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt class="text-slate-400">首次已刷</dt>
            <dd class="mt-0.5 text-slate-700">{{ formatDateTime(problem.solvedAt) }}</dd>
          </div>
          <div>
            <dt class="text-slate-400">最近已刷</dt>
            <dd class="mt-0.5 text-slate-700">{{ formatDateTime(problem.lastSolvedAt) }}</dd>
          </div>
          <div>
            <dt class="text-slate-400">累计次数</dt>
            <dd class="mt-0.5 text-slate-700">{{ problem.solveCount }} 次</dd>
          </div>
          <div>
            <dt class="text-slate-400">复习</dt>
            <dd class="mt-0.5 text-slate-700">{{ reviewStageText }}</dd>
          </div>
        </dl>
      </section>

      <section class="mt-3 rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
        <h3 class="text-sm font-medium text-slate-900">解题思路 / 代码</h3>
        <pre
          v-if="problem.approach"
          class="mt-2 whitespace-pre-wrap break-words font-mono text-[13px] leading-relaxed text-slate-700"
        >{{ problem.approach }}</pre>
        <p v-else class="mt-2 text-[13px] text-slate-400">还没写思路，点右上角「编辑」补上</p>
      </section>

      <section class="mt-3 rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
        <h3 class="text-sm font-medium text-slate-900">复盘备注</h3>
        <p v-if="problem.note" class="mt-2 whitespace-pre-wrap break-words text-[13px] leading-relaxed text-slate-700">
          {{ problem.note }}
        </p>
        <p v-else class="mt-2 text-[13px] text-slate-400">还没有备注</p>
      </section>

      <p class="mt-4 text-[12px] text-slate-400">
        创建于 {{ formatDateTime(problem.createdAt) }} · 更新于 {{ formatDateTime(problem.updatedAt) }}
      </p>
    </template>

    <BaseModal :open="confirmDelete" title="删除题目" width="sm" @close="confirmDelete = false">
      <p class="text-sm text-slate-600">
        确定删除「{{ problem?.title }}」？该题的复习流水与题单关联会一并清除，不可恢复。
      </p>
      <template #footer>
        <BaseButton size="sm" variant="secondary" @click="confirmDelete = false">取消</BaseButton>
        <BaseButton size="sm" variant="danger" :loading="busy" @click="remove">删除</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>
