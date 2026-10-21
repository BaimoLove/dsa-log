<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDataSourceLabel } from '@/repositories'

const source = computed(() => getDataSourceLabel())
const persisted = ref<boolean | null>(null)
const usage = ref<string>('—')

void (async () => {
  if (navigator.storage?.persisted) {
    persisted.value = await navigator.storage.persisted()
  }
  if (navigator.storage?.estimate) {
    const { usage: u = 0 } = await navigator.storage.estimate()
    usage.value = `${(u / 1024 / 1024).toFixed(2)} MB`
  }
})()
</script>

<template>
  <div>
    <PageHeader title="设置" description="数据源、导出备份与偏好" />

    <div class="space-y-3">
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="text-sm font-medium text-slate-800">数据源</h3>
        <p class="mt-1 text-[13px] text-slate-500">
          当前：<span class="font-medium text-slate-700">{{ source }}</span>
          <span class="text-slate-400">（VITE_DATA_SOURCE 控制，切到 http 即走 Spring Boot 接口）</span>
        </p>
      </section>

      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="text-sm font-medium text-slate-800">本地存储</h3>
        <dl class="mt-2 space-y-1 text-[13px] text-slate-500">
          <div class="flex justify-between">
            <dt>是否已申请持久化</dt>
            <dd class="text-slate-700">{{ persisted === null ? '—' : persisted ? '是' : '否' }}</dd>
          </div>
          <div class="flex justify-between">
            <dt>已用空间</dt>
            <dd class="tabular-nums text-slate-700">{{ usage }}</dd>
          </div>
        </dl>
        <p class="mt-2 text-[12px] text-slate-400">
          local 模式（Dexie）下这些指标有意义；切到 http 后数据在 MySQL，缓存清理不影响。
        </p>
      </section>

      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="text-sm font-medium text-slate-800">导出 / 导入</h3>
        <p class="mt-1 text-[13px] text-slate-500">M7 实现：JSON 全量备份、CSV 导出、replace/merge 两种导入模式。</p>
      </section>
    </div>
  </div>
</template>
