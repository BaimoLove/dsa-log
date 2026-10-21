<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProblemForm from '@/components/problem/ProblemForm.vue'
import { useProblemStore } from '@/stores/problem'
import { useUiStore } from '@/stores/ui'
import { toAppError } from '@/types/common'
import type { Problem } from '@/types/models'
import type { CreateProblemInput } from '@/types/repository'

const route = useRoute()
const router = useRouter()
const store = useProblemStore()
const ui = useUiStore()

const id = computed(() => (route.params.id as string | undefined) ?? null)
const isEdit = computed(() => Boolean(id.value))

const initial = ref<Problem | null>(null)
const loading = ref(false)
const submitting = ref(false)
const missing = ref(false)
const saved = ref(false)
const formRef = ref<InstanceType<typeof ProblemForm> | null>(null)

onMounted(async () => {
  if (!id.value) return
  loading.value = true
  try {
    const p = await store.fetchById(id.value)
    if (!p) missing.value = true
    else initial.value = p
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    loading.value = false
  }
})

async function onSubmit(payload: CreateProblemInput): Promise<void> {
  submitting.value = true
  try {
    if (isEdit.value && initial.value) {
      await store.updateProblem(initial.value.id, payload)
      saved.value = true
      ui.success('已保存')
      void router.replace(`/problems/${initial.value.id}`)
    } else {
      const created = await store.createProblem(payload)
      saved.value = true
      ui.success('已添加')
      void router.replace(`/problems/${created.id}`)
    }
  } catch (e) {
    ui.error(toAppError(e).message)
  } finally {
    submitting.value = false
  }
}

function cancel(): void {
  if (isEdit.value && initial.value) router.replace(`/problems/${initial.value.id}`)
  else router.push('/problems')
}

onBeforeRouteLeave(() => {
  if (saved.value || submitting.value) return true
  if (formRef.value?.isDirty) return window.confirm('有未保存的修改，确定离开？')
  return true
})
</script>

<template>
  <div>
    <PageHeader
      :title="isEdit ? '编辑题目' : '新建题目'"
      description="只记题名、难度、标签和你自己的思路，题干原文不落库"
    >
      <template #actions>
        <BaseButton size="sm" variant="ghost" @click="cancel">返回</BaseButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="rounded-xl border border-slate-200 bg-white py-16 text-center text-sm text-slate-400">
      加载中…
    </div>

    <div v-else-if="missing" class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="题目不存在或已删除" description="可能已被删除，回到题库看看">
        <BaseButton size="sm" variant="primary" @click="router.replace('/problems')">回题库</BaseButton>
      </EmptyState>
    </div>

    <ProblemForm v-else ref="formRef" :initial="initial" :submitting="submitting" @submit="onSubmit" @cancel="cancel" />
  </div>
</template>
