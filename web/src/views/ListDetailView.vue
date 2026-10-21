<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <div>
    <PageHeader title="题单详情" description="题单内题目与进度" />

    <div class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="题单详情将在 M3 实现" description="题单内分页、批量移出、继续刷下一题。">
        <BaseButton size="sm" @click="router.back()">返回</BaseButton>
      </EmptyState>
    </div>
  </div>
</template>
