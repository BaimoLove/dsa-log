<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    width?: 'sm' | 'md' | 'lg'
    closeOnBackdrop?: boolean
  }>(),
  { title: '', width: 'md', closeOnBackdrop: true },
)

const emit = defineEmits<{ close: [] }>()

const widthClass = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl' } as const

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      leave-active-class="transition-opacity duration-100"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
        <div
          class="absolute inset-0 bg-slate-900/40"
          @click="closeOnBackdrop && emit('close')"
        />
        <div
          class="relative w-full rounded-t-2xl bg-white shadow-xl sm:rounded-xl"
          :class="widthClass[width]"
          role="dialog"
          aria-modal="true"
        >
          <div
            v-if="title || $slots.header"
            class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-3.5"
          >
            <slot name="header">
              <h2 class="text-sm font-medium text-slate-900">{{ title }}</h2>
            </slot>
            <button
              type="button"
              class="-mr-1 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100"
              aria-label="关闭"
              @click="emit('close')"
            >
              <X class="size-4" />
            </button>
          </div>

          <div class="max-h-[70vh] overflow-y-auto px-5 py-4">
            <slot />
          </div>

          <div v-if="$slots.footer" class="flex justify-end gap-2 border-t border-slate-100 px-5 py-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
