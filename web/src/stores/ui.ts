import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  actionLabel?: string
  onAction?: () => void
}

let seq = 0

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<ToastItem[]>([])
  const drawerOpen = ref(false)

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function toast(
    message: string,
    type: ToastType = 'info',
    options: { duration?: number; actionLabel?: string; onAction?: () => void } = {},
  ): number {
    const id = ++seq
    const duration = options.duration ?? (options.actionLabel ? 6000 : 2600)
    toasts.value.push({
      id,
      type,
      message,
      ...(options.actionLabel ? { actionLabel: options.actionLabel } : {}),
      ...(options.onAction ? { onAction: options.onAction } : {}),
    })
    if (duration > 0) {
      window.setTimeout(() => dismiss(id), duration)
    }
    return id
  }

  function success(message: string) {
    return toast(message, 'success')
  }
  function error(message: string) {
    return toast(message, 'error')
  }

  function openDrawer() {
    drawerOpen.value = true
  }
  function closeDrawer() {
    drawerOpen.value = false
  }
  function toggleDrawer() {
    drawerOpen.value = !drawerOpen.value
  }

  return { toasts, drawerOpen, toast, success, error, dismiss, openDrawer, closeDrawer, toggleDrawer }
})
