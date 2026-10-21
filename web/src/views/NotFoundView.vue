<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<template>
  <div>
    <PageHeader title="页面不存在" />
    <div class="rounded-xl border border-slate-200 bg-white">
      <EmptyState title="404" description="这个地址没有对应的页面，可能是链接过期或手输错了。">
        <RouterLink to="/">
          <BaseButton size="sm">回到概览</BaseButton>
        </RouterLink>
      </EmptyState>
    </div>
  </div>
</template>
