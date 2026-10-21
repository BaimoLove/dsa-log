<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<template>
  <div>
    <PageHeader title="复习" description="1 / 3 / 7 / 15 天节奏，三档评分" />

    <div class="rounded-xl border border-slate-200 bg-white">
      <EmptyState
        title="复习队列将在 M6 实现"
        description="逐题卡片、翻面看答案、键盘 1/2/3 评分；忘记的题明天再来。"
      />
    </div>
  </div>
</template>
