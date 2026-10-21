import type { ApiClient } from '@/api/client'
import { ENDPOINTS } from '@/api/endpoints'
import { AppError } from '@/types/common'
import type { BackupPayload, BackupRepository, ImportMode, ImportResult } from '@/types/repository'
import type { BackupDto, ImportResultDto } from './dto'
import { toBackupPayload, toImportResult } from './mapper'

/**
 * 备份仓储（HTTP）。
 *
 * 导出端点直接返回附件流（不是 Result 包装），所以走 client 的 raw 通道再自己解析：
 * 浏览器下载要的是文件本身，套一层 {code,data} 反而让前端多解一次。
 *
 * replace 的二次确认在前端是「手输 REPLACE」的 UI 闸门，这里根据 mode 自动带上
 * `confirm=REPLACE`——真正的守门人是后端，它没有这个参数就返 400。
 */
export function createHttpBackupRepository(client: ApiClient): BackupRepository {
  return {
    async exportAll(): Promise<BackupPayload> {
      const text = await client.request<string>(ENDPOINTS.export, {
        query: { format: 'json' },
        raw: true,
      })
      let raw: BackupDto
      try {
        raw = JSON.parse(text) as BackupDto
      } catch {
        throw new AppError('IMPORT_INVALID', '导出内容不是合法 JSON')
      }
      return toBackupPayload(raw)
    },

    async exportCsv(): Promise<string> {
      return client.request<string>(ENDPOINTS.export, { query: { format: 'csv' }, raw: true })
    },

    async importAll(payload: BackupPayload, mode: ImportMode): Promise<ImportResult> {
      const dto = await client.request<ImportResultDto>(ENDPOINTS.import, {
        method: 'POST',
        query: { mode, confirm: mode === 'replace' ? 'REPLACE' : undefined },
        body: payload,
      })
      return toImportResult(dto)
    },
  }
}
