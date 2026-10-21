<script setup lang="ts">
import { computed } from 'vue'
import { DIFFICULTY_LABEL, type Difficulty } from '@/types/enums'

const props = withDefaults(defineProps<{ difficulty: Difficulty; size?: 'sm' | 'md' }>(), { size: 'sm' })

const TONE: Record<Difficulty, string> = {
  easy: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  medium: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  hard: 'bg-red-50 text-red-700 ring-red-600/15',
}

const classes = computed(() => [
  'inline-flex items-center rounded-md font-medium ring-1 ring-inset',
  props.size === 'sm' ? 'px-1.5 py-0.5 text-[11px]' : 'px-2 py-1 text-xs',
  TONE[props.difficulty],
])
</script>

<template>
  <span :class="classes">{{ DIFFICULTY_LABEL[difficulty] }}</span>
</template>
