<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Dices, Keyboard, Menu, TriangleAlert } from 'lucide-vue-next'
import BaseButton from '@/components/common/BaseButton.vue'
import { useUiStore } from '@/stores/ui'
import { useRandomStore } from '@/stores/random'
import { storageState } from '@/db'

const ui = useUiStore()
const random = useRandomStore()
const route = useRoute()
const title = computed(() => (route.meta.title as string | undefined) ?? '刷题记录')
const degraded = computed(() => !storageState.available)
</script>

<template>
  <header
    class="sticky top-0 z-20 flex h-16 shrink-0 items-center gap-3 border-b border-slate-200 bg-white/85 px-4 backdrop-blur md:px-6"
  >
    <button
      type="button"
      class="-ml-1 rounded-lg p-2 text-slate-500 hover:bg-slate-100 md:hidden"
      aria-label="打开菜单"
      @click="ui.openDrawer()"
    >
      <Menu class="size-5" />
    </button>

    <h1 class="truncate text-base font-medium text-slate-900">{{ title }}</h1>

    <div class="ml-auto flex items-center gap-2">
      <div
        v-if="degraded"
        class="flex items-center gap-1.5 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs text-amber-700"
        :title="storageState.error ?? ''"
      >
        <TriangleAlert class="size-3.5" />
        <span class="hidden sm:inline">存储不可用，当前为只读</span>
      </div>
      <BaseButton size="sm" variant="ghost" title="快捷键（?）" @click="ui.openShortcuts()">
        <Keyboard class="size-4" />
      </BaseButton>
      <BaseButton size="sm" variant="secondary" title="随机抽一道题（R）" @click="random.openModal()">
        <Dices class="size-4" />
        <span class="hidden sm:inline">随机一题</span>
      </BaseButton>
    </div>
  </header>
</template>
