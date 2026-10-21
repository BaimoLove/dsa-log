<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<template>
  <div>
    <PageHeader title="标签" description="改名全站即时生效，删除自动解绑" />

    <div class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="标签管理将在 M3 实现" description="改名、改色、引用计数、删除并解绑。" />
    </div>
  </div>
</template>
