import { defineStore } from 'pinia'
import { ref } from 'vue'
import { settingsRepo } from '@/repositories'
import { DEFAULT_SETTINGS, type AppSettings } from '@/types/models'

/**
 * 设置 store。整份读取、部分保存：页面只改需要的字段，
 * 落库由仓储负责与默认值合并，避免页面里到处写 `?? DEFAULT_SETTINGS.x`。
 */
export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<AppSettings>({ ...DEFAULT_SETTINGS })
  const loading = ref(false)
  const saving = ref(false)
  const ready = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      settings.value = await settingsRepo.get()
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  async function save(patch: Partial<AppSettings>): Promise<void> {
    saving.value = true
    try {
      settings.value = await settingsRepo.save(patch)
    } finally {
      saving.value = false
    }
  }

  return { settings, loading, saving, ready, load, save }
})
