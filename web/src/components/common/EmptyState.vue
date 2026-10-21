<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  { description: '' },
)
</script>

<template>
  <div class="flex flex-col items-center justify-center px-6 py-16 text-center">
    <div class="mb-3 flex size-12 items-center justify-center rounded-full bg-slate-100">
      <Inbox class="size-6 text-slate-400" :stroke-width="1.6" />
    </div>
    <p class="text-sm font-medium text-slate-700">{{ title }}</p>
    <p v-if="description" class="mt-1 max-w-sm text-[13px] leading-relaxed text-slate-400">
      {{ description }}
    </p>
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>
