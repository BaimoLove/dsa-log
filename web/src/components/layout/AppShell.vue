<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { X } from 'lucide-vue-next'
import SideBar from './SideBar.vue'
import TopBar from './TopBar.vue'
import RandomPickModal from '@/components/problem/RandomPickModal.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const isDesktop = useMediaQuery('(min-width: 768px)')

const showOverlay = computed(() => !isDesktop.value && ui.drawerOpen)

function close() {
  ui.closeDrawer()
}
</script>

<template>
  <div class="flex h-full">
    <!-- 桌面：常驻侧栏 -->
    <aside v-if="isDesktop" class="w-60 shrink-0 border-r border-slate-200">
      <SideBar />
    </aside>

    <!-- 移动：抽屉 -->
    <template v-else>
      <Transition
        enter-active-class="transition-opacity duration-200"
        leave-active-class="transition-opacity duration-150"
        enter-from-class="opacity-0"
        leave-to-class="opacity-0"
      >
        <div v-if="showOverlay" class="fixed inset-0 z-40 bg-slate-900/40" @click="close" />
      </Transition>
      <Transition
        enter-active-class="transition-transform duration-200"
        leave-active-class="transition-transform duration-150"
        enter-from-class="-translate-x-full"
        leave-to-class="-translate-x-full"
      >
        <aside v-if="showOverlay" class="fixed inset-y-0 left-0 z-50 w-64 shadow-xl">
          <button
            type="button"
            class="absolute right-3 top-4 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100"
            aria-label="关闭菜单"
            @click="close"
          >
            <X class="size-4" />
          </button>
          <SideBar @navigate="close" />
        </aside>
      </Transition>
    </template>

    <div class="flex min-w-0 flex-1 flex-col">
      <TopBar />
      <main class="flex-1 overflow-y-auto px-4 py-5 md:px-6 md:py-6">
        <div class="mx-auto w-full max-w-6xl">
          <slot />
        </div>
      </main>
    </div>

    <!-- 全局单例：TopBar / 概览页 / 题库页都通过 random store 唤起 -->
    <RandomPickModal />
  </div>
</template>
