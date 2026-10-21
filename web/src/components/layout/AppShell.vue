<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { X } from 'lucide-vue-next'
import SideBar from './SideBar.vue'
import TopBar from './TopBar.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import RandomPickModal from '@/components/problem/RandomPickModal.vue'
import { useUiStore } from '@/stores/ui'
import { useRandomStore } from '@/stores/random'
import { useGlobalHotkeys } from '@/composables/useGlobalHotkeys'

const ui = useUiStore()
const random = useRandomStore()
const router = useRouter()
const route = useRoute()
const isDesktop = useMediaQuery('(min-width: 768px)')

const showOverlay = computed(() => !isDesktop.value && ui.drawerOpen)

/* 全局快捷键：导航与弹窗不在任何单个页面里，挂在壳层最稳 */
useGlobalHotkeys({
  search: () => {
    if (route.name !== 'problems') void router.push({ name: 'problems' })
    // 先置位再挂载/聚焦：题库页 watch 到 true 后会聚焦并消费
    ui.requestSearchFocus()
  },
  random: () => random.openModal(),
  help: () => ui.openShortcuts(),
})

const SHORTCUTS = [
  { keys: ['Ctrl', 'K'], desc: '打开题库搜索' },
  { keys: ['/'], desc: '同上，单手操作更顺' },
  { keys: ['R'], desc: '随机抽一道题' },
  { keys: ['?'], desc: '显示这份快捷键说明' },
  { keys: ['Esc'], desc: '关闭弹窗 / 退出搜索框' },
]

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

    <BaseModal :open="ui.shortcutsOpen" title="快捷键" width="sm" @close="ui.closeShortcuts()">
      <dl class="divide-y divide-slate-100 text-[13px]">
        <div v-for="item in SHORTCUTS" :key="item.desc" class="flex items-center justify-between gap-3 py-2">
          <dt class="text-slate-600">{{ item.desc }}</dt>
          <dd class="flex shrink-0 items-center gap-1">
            <template v-for="(key, idx) in item.keys" :key="key">
              <span v-if="idx > 0" class="text-[11px] text-slate-300">+</span>
              <kbd
                class="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 font-mono text-[11px] text-slate-600"
              >
                {{ key }}
              </kbd>
            </template>
          </dd>
        </div>
      </dl>
      <p class="mt-3 text-[12px] text-slate-400">在输入框里打字时，单键快捷键不生效，避免误触。</p>
    </BaseModal>
  </div>
</template>
