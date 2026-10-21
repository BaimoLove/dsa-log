<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Check, Dices, ExternalLink, RefreshCw } from 'lucide-vue-next'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import DifficultyBadge from './DifficultyBadge.vue'
import TagChip from './TagChip.vue'
import { useRandomStore } from '@/stores/random'
import { useUiStore } from '@/stores/ui'
import { formatDate } from '@/utils/date'
import { toAppError } from '@/types/common'

const random = useRandomStore()
const ui = useUiStore()
const router = useRouter()

const marking = ref(false)

const current = computed(() => random.current)

const meta = computed(() => {
  const p = current.value
  if (!p) return ''
  if (p.status !== 'solved') return '未刷 · 抽到就现在做'
  return `已刷 ${p.solveCount} 次 · 上次 ${formatDate(p.lastSolvedAt)}`
})

async function markSolved(): Promise<void> {
  if (!current.value) return
  marking.value = true
  try {
    await random.markCurrentSolved()
    ui.success('已标记为刷过')
    random.close()
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    marking.value = false
  }
}

function openDetail(): void {
  if (!current.value) return
  const id = current.value.id
  random.close()
  void router.push(`/problems/${id}`)
}
</script>

<template>
  <BaseModal :open="random.open" width="md" @close="random.close()">
    <template #header>
      <div class="flex items-center gap-2 text-sm font-medium text-slate-900">
        <Dices class="size-4 text-blue-600" />
        随机一题
      </div>
    </template>

    <div v-if="random.empty" class="py-8 text-center">
      <p class="text-sm font-medium text-slate-700">题库还是空的</p>
      <p class="mt-1 text-[13px] text-slate-400">先录入几道题，才有得抽</p>
      <RouterLink to="/problems/new" @click="random.close()">
        <BaseButton class="mt-4" variant="primary" size="sm">去添加题目</BaseButton>
      </RouterLink>
    </div>

    <div v-else-if="current">
      <div class="rounded-xl border border-slate-200 bg-gradient-to-b from-slate-50 to-white px-5 py-6 text-center">
        <p class="text-[12px] text-slate-400">{{ random.poolHint }}</p>
        <h3 class="mt-2 break-words text-xl font-medium text-slate-900">{{ current.title }}</h3>

        <div class="mt-3 flex flex-wrap items-center justify-center gap-1.5">
          <DifficultyBadge :difficulty="current.difficulty" size="md" />
          <TagChip v-for="t in current.tags" :key="t.id" :tag="t" />
        </div>

        <p class="mt-3 text-[12px] text-slate-400">{{ meta }}</p>

        <a
          v-if="current.url"
          :href="current.url"
          target="_blank"
          rel="noopener noreferrer"
          class="mt-3 inline-flex items-center gap-1 text-[12px] font-medium text-blue-600 hover:underline"
        >
          打开原题
          <ExternalLink class="size-3" />
        </a>
      </div>
    </div>

    <div v-else class="py-10 text-center text-sm text-slate-400">正在抽…</div>

    <template #footer>
      <BaseButton variant="ghost" size="sm" :disabled="random.loading" @click="random.draw()">
        <RefreshCw class="size-3.5" :class="random.loading ? 'animate-spin' : ''" />
        换一题
      </BaseButton>
      <BaseButton size="sm" :disabled="!current" @click="openDetail">看详情</BaseButton>
      <BaseButton variant="primary" size="sm" :disabled="!current" :loading="marking" @click="markSolved">
        <Check class="size-3.5" />
        标记已刷
      </BaseButton>
    </template>
  </BaseModal>
</template>
