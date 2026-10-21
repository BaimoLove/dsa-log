<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Database, Download, HardDrive, RotateCcw, Save, Upload } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { backupRepo, getDataSourceLabel } from '@/repositories'
import { useProblemStore } from '@/stores/problem'
import { useSettingsStore } from '@/stores/settings'
import { useUiStore } from '@/stores/ui'
import { parseBackup, serializeBackup } from '@/services/backup'
import { toAppError } from '@/types/common'
import type { ImportMode, ImportResult } from '@/types/repository'
import { dayjs } from '@/utils/date'

/**
 * 设置页。三件事：偏好、备份、本地存储状态。
 *
 * 导入的二次确认刻意不用 `confirm()`：replace 会清空六张表，必须让人
 * **手输 REPLACE** 才能点下去，弹窗点快了容易误触。
 */
const settingsStore = useSettingsStore()
const problemStore = useProblemStore()
const ui = useUiStore()

const dailyLimit = ref(20)
const pageSize = ref(20)
const exporting = ref(false)
const importing = ref(false)
const mode = ref<ImportMode>('merge')
const confirmText = ref('')
const fileName = ref('')
const importResult = ref<ImportResult | null>(null)
const error = ref('')

const source = computed(() => getDataSourceLabel())
const persisted = ref<boolean | null>(null)
const usage = ref<string>('—')

const replaceReady = computed(() => mode.value === 'merge' || confirmText.value.trim() === 'REPLACE')
const dirty = computed(
  () => dailyLimit.value !== settingsStore.settings.dailyReviewLimit || pageSize.value !== settingsStore.settings.pageSize,
)

onMounted(async () => {
  await settingsStore.load()
  dailyLimit.value = settingsStore.settings.dailyReviewLimit
  pageSize.value = settingsStore.settings.pageSize

  if (navigator.storage?.persisted) persisted.value = await navigator.storage.persisted()
  if (navigator.storage?.estimate) {
    const { usage: u = 0 } = await navigator.storage.estimate()
    usage.value = `${(u / 1024 / 1024).toFixed(2)} MB`
  }
})

function download(name: string, content: string, type: string): void {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  // 立刻 revoke 在部分浏览器上会让下载中断，延后一拍
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function stamp(): string {
  return dayjs().format('YYYYMMDD-HHmm')
}

async function exportJson(): Promise<void> {
  exporting.value = true
  error.value = ''
  try {
    const payload = await backupRepo.exportAll()
    download(`dsa-log-backup-${stamp()}.json`, serializeBackup(payload), 'application/json;charset=utf-8')
    ui.success(`已导出 ${payload.problems.length} 道题、${payload.lists.length} 个题单`)
  } catch (e) {
    error.value = toAppError(e).message
  } finally {
    exporting.value = false
  }
}

async function exportCsv(): Promise<void> {
  exporting.value = true
  error.value = ''
  try {
    const csv = await backupRepo.exportCsv()
    download(`dsa-log-problems-${stamp()}.csv`, csv, 'text/csv;charset=utf-8')
    ui.success('已导出 CSV（Excel 可直接打开）')
  } catch (e) {
    error.value = toAppError(e).message
  } finally {
    exporting.value = false
  }
}

async function savePreferences(): Promise<void> {
  error.value = ''
  try {
    await settingsStore.save({
      dailyReviewLimit: Math.min(200, Math.max(1, Math.floor(dailyLimit.value || 20))),
      pageSize: pageSize.value,
    })
    dailyLimit.value = settingsStore.settings.dailyReviewLimit
    // 每页条数是题库 store 的入参，改完立刻生效，不必等下次进入题库页
    problemStore.setSize(settingsStore.settings.pageSize)
    ui.success('设置已保存')
  } catch (e) {
    error.value = toAppError(e).message
  }
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  fileName.value = file.name
  importResult.value = null
  error.value = ''

  importing.value = true
  try {
    const payload = parseBackup(await file.text())
    importResult.value = await backupRepo.importAll(payload, mode.value)
    await settingsStore.load()
    dailyLimit.value = settingsStore.settings.dailyReviewLimit
    pageSize.value = settingsStore.settings.pageSize
    ui.success(
      importResult.value.mode === 'replace' ? '已整库替换，刷新页面查看' : '已合并导入，刷新页面查看',
    )
  } catch (e) {
    error.value = toAppError(e).message
  } finally {
    importing.value = false
    // 同一个文件连续选两次也要能触发 change
    input.value = ''
  }
}

function reload(): void {
  window.location.reload()
}
</script>

<template>
  <div>
    <PageHeader title="设置" description="数据源、复习偏好与备份" />

    <p v-if="error" class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] text-red-700">{{ error }}</p>

    <div class="space-y-3">
      <!-- 偏好 -->
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <RotateCcw class="size-4 text-slate-400" />
          复习与列表
        </h3>
        <div class="mt-3 grid gap-4 sm:grid-cols-2">
          <label class="block">
            <span class="text-[13px] text-slate-600">每次复习最多取几道</span>
            <input
              v-model.number="dailyLimit"
              type="number"
              min="1"
              max="200"
              class="mt-1 h-9 w-full rounded-lg border border-slate-300 px-3 text-[13px] text-slate-800 outline-none focus:border-blue-500"
            />
            <span class="mt-1 block text-[12px] text-slate-400">
              只影响一次载入的队列长度，队列里还有多少道看「待复习」总数
            </span>
          </label>
          <label class="block">
            <span class="text-[13px] text-slate-600">每页显示条数</span>
            <select
              v-model.number="pageSize"
              class="mt-1 h-9 w-full rounded-lg border border-slate-300 bg-white px-3 text-[13px] text-slate-800 outline-none focus:border-blue-500"
            >
              <option v-for="n in [10, 20, 50, 100]" :key="n" :value="n">{{ n }} 条</option>
            </select>
            <span class="mt-1 block text-[12px] text-slate-400">题库与题单内分页共用，上限 100</span>
          </label>
        </div>
        <div class="mt-3 flex items-center gap-2">
          <BaseButton variant="primary" size="sm" :loading="settingsStore.saving" :disabled="!dirty" @click="savePreferences">
            <Save class="size-4" />
            保存
          </BaseButton>
          <span v-if="dirty" class="text-[12px] text-amber-600">有未保存的修改</span>
        </div>
      </section>

      <!-- 导出 -->
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <Download class="size-4 text-slate-400" />
          导出
        </h3>
        <p class="mt-1 text-[13px] text-slate-500">
          JSON 是整库备份（题目、标签、题单、复习流水、设置），换台机器导回来即可还原；CSV 只导题目，给人看。
        </p>
        <div class="mt-3 flex flex-wrap gap-2">
          <BaseButton variant="secondary" size="sm" :loading="exporting" @click="exportJson">
            <Download class="size-4" />
            导出 JSON 备份
          </BaseButton>
          <BaseButton variant="secondary" size="sm" :loading="exporting" @click="exportCsv">
            <Download class="size-4" />
            导出 CSV
          </BaseButton>
        </div>
      </section>

      <!-- 导入 -->
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <Upload class="size-4 text-slate-400" />
          导入
        </h3>
        <div class="mt-3 space-y-3">
          <div class="flex flex-wrap gap-4">
            <label class="flex items-center gap-1.5 text-[13px] text-slate-700">
              <input v-model="mode" type="radio" value="merge" class="size-3.5" />
              合并（同 id 按 updatedAt 取新）
            </label>
            <label class="flex items-center gap-1.5 text-[13px] text-slate-700">
              <input v-model="mode" type="radio" value="replace" class="size-3.5" />
              替换（先清空原有数据）
            </label>
          </div>

          <div
            v-if="mode === 'replace'"
            class="rounded-lg border border-red-200 bg-red-50/60 px-3 py-2.5"
          >
            <p class="text-[12px] font-medium text-red-700">
              替换会先删掉现有全部题目、标签、题单与复习流水，且不可撤销。
            </p>
            <label class="mt-2 block">
              <span class="text-[12px] text-red-700">输入 REPLACE 以确认</span>
              <input
                v-model="confirmText"
                type="text"
                placeholder="REPLACE"
                class="mt-1 h-8 w-48 rounded-lg border border-red-300 px-2 text-[13px] outline-none focus:border-red-500"
              />
            </label>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <label
              class="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg bg-blue-600 px-3 text-[13px] font-medium text-white transition-colors hover:bg-blue-700"
              :class="replaceReady ? '' : 'pointer-events-none opacity-50'"
            >
              <Upload class="size-4" />
              {{ importing ? '导入中…' : '选择备份文件' }}
              <input
                type="file"
                accept="application/json,.json"
                class="hidden"
                :disabled="!replaceReady || importing"
                @change="onFileChange"
              />
            </label>
            <span v-if="fileName" class="text-[12px] text-slate-500">{{ fileName }}</span>
          </div>

          <div v-if="importResult" class="rounded-lg bg-slate-50 px-3 py-2.5 text-[12px] text-slate-600">
            <p class="font-medium text-slate-700">
              导入完成（{{ importResult.mode === 'replace' ? '替换' : '合并' }}）
            </p>
            <p class="mt-1 tabular-nums">
              题目 新增 {{ importResult.problems.created }} / 更新 {{ importResult.problems.updated }} /
              跳过 {{ importResult.problems.skipped }}；标签 新增 {{ importResult.tags.created }} /
              更新 {{ importResult.tags.updated }} / 跳过 {{ importResult.tags.skipped }}；题单 新增
              {{ importResult.lists.created }} / 更新 {{ importResult.lists.updated }}；关联
              {{ importResult.listItems }} 条；流水 {{ importResult.reviews }} 条
            </p>
            <BaseButton class="mt-2" size="sm" variant="secondary" @click="reload">刷新页面查看</BaseButton>
          </div>
        </div>
      </section>

      <!-- 数据源与本地存储 -->
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <Database class="size-4 text-slate-400" />
          数据源
        </h3>
        <p class="mt-1 text-[13px] text-slate-500">
          当前：<span class="font-medium text-slate-700">{{ source }}</span>
          <span class="text-slate-400">（VITE_DATA_SOURCE 控制：local 走浏览器 IndexedDB，http 走 Spring Boot + MySQL）</span>
        </p>
      </section>

      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <h3 class="flex items-center gap-1.5 text-sm font-medium text-slate-800">
          <HardDrive class="size-4 text-slate-400" />
          本地存储
        </h3>
        <dl class="mt-2 space-y-1 text-[13px] text-slate-500">
          <div class="flex justify-between">
            <dt>是否已申请持久化</dt>
            <dd class="text-slate-700">{{ persisted === null ? '—' : persisted ? '是' : '否' }}</dd>
          </div>
          <div class="flex justify-between">
            <dt>已用空间</dt>
            <dd class="tabular-nums text-slate-700">{{ usage }}</dd>
          </div>
        </dl>
        <p class="mt-2 text-[12px] text-slate-400">
          local 模式下这些指标有意义；切到 http 后数据在 MySQL，清理浏览器缓存不影响。
        </p>
      </section>
    </div>
  </div>
</template>
