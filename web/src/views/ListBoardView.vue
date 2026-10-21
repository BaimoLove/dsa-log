<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<template>
  <div>
    <PageHeader title="题单" description="按专题分组，一题可属于多个题单" />

    <div class="rounded-xl border border-slate-200 bg-white">
      <EmptyState
        title="题单看板将在 M3 实现"
        description="每个题单显示已刷/总数进度环，支持批量移出与继续刷下一题。"
      />
    </div>
  </div>
</template>
