<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  LayoutDashboard,
  Layers,
  ListChecks,
  RotateCcw,
  Settings,
  Tags,
  type LucideIcon,
} from 'lucide-vue-next'
import { navRoutes } from '@/router/routes'

const iconMap: Record<string, LucideIcon> = {
  LayoutDashboard,
  ListChecks,
  Layers,
  Tags,
  RotateCcw,
  Settings,
}

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()

const items = computed(() =>
  navRoutes.map((r) => ({
    name: String(r.name),
    path: r.path,
    title: (r.meta?.title as string) ?? '',
    icon: iconMap[(r.meta?.icon as string) ?? ''] ?? ListChecks,
  })),
)

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <div class="flex h-full flex-col bg-white">
    <div class="flex h-16 shrink-0 items-center gap-2.5 px-5">
      <div class="flex size-8 items-center justify-center rounded-lg bg-blue-600 text-sm font-semibold text-white">
        D
      </div>
      <div class="leading-tight">
        <div class="text-sm font-medium text-slate-900">刷题记录</div>
        <div class="text-[11px] text-slate-400">DSA Log</div>
      </div>
    </div>

    <nav class="flex-1 space-y-1 px-3 py-2">
      <RouterLink
        v-for="item in items"
        :key="item.name"
        :to="item.path"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors"
        :class="
          isActive(item.path)
            ? 'bg-blue-50 font-medium text-blue-700'
            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
        "
        @click="emit('navigate')"
      >
        <component :is="item.icon" class="size-[18px] shrink-0" :stroke-width="1.8" />
        <span>{{ item.title }}</span>
      </RouterLink>
    </nav>

    <div class="border-t border-slate-100 px-5 py-3">
      <p class="text-[11px] leading-relaxed text-slate-400">
        数据存于 MySQL<br />
        清浏览器缓存不影响
      </p>
    </div>
  </div>
</template>
