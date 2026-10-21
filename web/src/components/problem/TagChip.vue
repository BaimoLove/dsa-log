<script setup lang="ts">
import { computed } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    tag: { id: string; name: string; color: string }
    /** 可点删除（表单里移除） */
    removable?: boolean
    /** 可点击切换选中态 */
    active?: boolean
    interactive?: boolean
  }>(),
  { removable: false, active: false, interactive: false },
)

const emit = defineEmits<{ remove: []; toggle: [] }>()

/** 用标签自身颜色做浅底 + 深字，不引入额外色阶表 */
const style = computed(() => {
  const c = props.tag.color || '#6366f1'
  return {
    color: c,
    backgroundColor: props.active ? `${c}24` : `${c}12`,
    borderColor: props.active ? `${c}80` : `${c}33`,
  }
})
</script>

<template>
  <component
    :is="interactive || removable ? 'button' : 'span'"
    :type="interactive || removable ? 'button' : undefined"
    class="inline-flex max-w-full items-center gap-1 rounded-md border px-1.5 py-0.5 text-[11px] font-medium"
    :class="interactive ? 'transition-colors hover:brightness-95' : ''"
    :style="style"
    @click="interactive && emit('toggle')"
  >
    <span class="truncate">{{ tag.name }}</span>
    <span
      v-if="removable"
      class="-mr-0.5 rounded p-0.5 hover:bg-black/5"
      role="button"
      aria-label="移除"
      @click.stop="emit('remove')"
    >
      <X class="size-3" />
    </span>
  </component>
</template>
