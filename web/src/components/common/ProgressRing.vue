<script setup lang="ts">
import { computed } from 'vue'
import { toPercent } from '@/utils/progress'

/**
 * 纯展示的进度环。中心内容用插槽，看板卡片放百分比、详情页放「12/30」。
 * 空题单不画进度弧（画 0% 弧和没画看起来一样，反而让人以为是加载失败）。
 */
const props = withDefaults(
  defineProps<{
    solved: number
    total: number
    size?: number
    stroke?: number
    color?: string
  }>(),
  { size: 56, stroke: 6, color: '#3b82f6' },
)

const percent = computed(() => toPercent(props.solved, props.total))
const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const dashOffset = computed(() => circumference.value * (1 - percent.value / 100))

const label = computed(() =>
  props.total > 0 ? `已完成 ${percent.value}%（${props.solved}/${props.total}）` : '空题单，还没有题目',
)
</script>

<template>
  <div
    class="relative inline-flex shrink-0 items-center justify-center"
    :style="{ width: `${size}px`, height: `${size}px` }"
    role="img"
    :aria-label="label"
  >
    <!-- 旋转用 SVG 自己的 transform：CSS 的 transform-origin 在 SVG 子树里默认是 0 0，
         套 -rotate-90 会让环跑到画布外，这里直接用 rotate(角度 cx cy) 绕圆心转 -->
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <g :transform="`rotate(-90 ${size / 2} ${size / 2})`">
        <circle
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          fill="none"
          stroke="#e2e8f0"
          :stroke-width="stroke"
        />
        <circle
          v-if="total > 0"
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          fill="none"
          :stroke="color"
          :stroke-width="stroke"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
          class="transition-[stroke-dashoffset] duration-500 ease-out"
        />
      </g>
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center leading-none">
      <slot>
        <span
          class="text-[13px] font-semibold tabular-nums"
          :class="total > 0 ? 'text-slate-700' : 'text-slate-300'"
        >
          {{ total > 0 ? `${percent}%` : '—' }}
        </span>
      </slot>
    </div>
  </div>
</template>
