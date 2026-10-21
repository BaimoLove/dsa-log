import { z } from 'zod'
import { DIFFICULTIES, RATINGS, REVIEW_KINDS, STATUSES } from '@/types/enums'
import { DEFAULT_SETTINGS, type AppSettings, type Problem } from '@/types/models'
import type { BackupPayload } from '@/types/repository'
import { AppError } from '@/types/common'
import { dayjs } from '@/utils/date'

/**
 * 备份文件的解析与序列化（纯函数，不碰 DB）。
 *
 * 两个约定：
 * 1. 顶层带 `schemaVersion`，**不匹配直接拒**（422 IMPORT_INVALID）。不做
 *    「尽力而为」的兼容：字段语义变过之后，猜着迁移比报错更难查。
 * 2. 缺失的可选字段一律补默认值（zod 的 default），这样旧文件里没写
 *    `note`、`sortOrder` 也能导进来，而不会到处 undefined。
 */
export const BACKUP_APP = 'dsa-log'
export const BACKUP_SCHEMA_VERSION = 1

const nullableNumber = z.number().nullable().default(null)

const tagSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  color: z.string().default('#6366f1'),
  createdAt: z.number().default(0),
})

const problemSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  difficulty: z.enum(DIFFICULTIES),
  status: z.enum(STATUSES).default('todo'),
  approach: z.string().default(''),
  note: z.string().default(''),
  source: z.string().default(''),
  url: z.string().default(''),
  tagIds: z.array(z.string()).default([]),
  // 备份里的 tags 只是导出时的快照，导入时一律丢弃（标签详情按 tagIds 现拼），
  // 所以这里不校验结构，只保证是个数组
  tags: z.array(z.unknown()).default([]),
  solvedAt: nullableNumber,
  lastSolvedAt: nullableNumber,
  solveCount: z.number().int().nonnegative().default(0),
  reviewStage: z.number().int().min(0).max(4).default(0),
  nextReviewAt: nullableNumber,
  lastReviewedAt: nullableNumber,
  archived: z.union([z.literal(0), z.literal(1)]).default(0),
  createdAt: z.number().default(0),
  updatedAt: z.number().default(0),
})

const listSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  description: z.string().default(''),
  color: z.string().default('#6366f1'),
  sortOrder: z.number().int().default(0),
  createdAt: z.number().default(0),
  updatedAt: z.number().default(0),
})

const listItemSchema = z.object({
  listId: z.string().min(1),
  problemId: z.string().min(1),
  sortOrder: z.number().int().default(0),
  addedAt: z.number().default(0),
})

const reviewSchema = z.object({
  id: z.string().min(1),
  problemId: z.string().min(1),
  kind: z.enum(REVIEW_KINDS),
  rating: z.enum(RATINGS).nullable().default(null),
  fromStage: nullableNumber,
  toStage: nullableNumber,
  intervalDays: nullableNumber,
  nextReviewAt: nullableNumber,
  reviewedAt: z.number().default(0),
  note: z.string().default(''),
})

const settingsSchema = z.object({
  dailyReviewLimit: z.number().int().positive().default(DEFAULT_SETTINGS.dailyReviewLimit),
  pageSize: z.number().int().positive().default(DEFAULT_SETTINGS.pageSize),
  reviewIntervals: z.array(z.number().positive()).default([...DEFAULT_SETTINGS.reviewIntervals]),
  overdueFromNow: z.boolean().default(DEFAULT_SETTINGS.overdueFromNow),
})

const backupSchema = z.object({
  schemaVersion: z.number(),
  exportedAt: z.number().default(0),
  problems: z.array(problemSchema).default([]),
  tags: z.array(tagSchema).default([]),
  lists: z.array(listSchema).default([]),
  listItems: z.array(listItemSchema).default([]),
  reviews: z.array(reviewSchema).default([]),
  settings: settingsSchema.default(DEFAULT_SETTINGS),
})

export function serializeBackup(payload: BackupPayload): string {
  return JSON.stringify(payload, null, 2)
}

/**
 * 解析备份文件。任何结构问题都收敛成 422：前端只提示「文件格式不正确」，
 * 不把 zod 的路径泄漏给用户；原始细节放 message 里方便自己排查。
 */
export function parseBackup(text: string): BackupPayload {
  let raw: unknown
  try {
    raw = JSON.parse(text)
  } catch {
    throw new AppError('IMPORT_INVALID', '不是合法的 JSON 文件')
  }

  const version = (raw as { schemaVersion?: unknown })?.schemaVersion
  if (version !== BACKUP_SCHEMA_VERSION) {
    throw new AppError(
      'IMPORT_INVALID',
      `备份版本不匹配：文件是 ${String(version)}，当前只支持 ${BACKUP_SCHEMA_VERSION}`,
    )
  }

  const parsed = backupSchema.safeParse(raw)
  if (!parsed.success) {
    const first = parsed.error.issues[0]
    throw new AppError(
      'IMPORT_INVALID',
      first ? `备份内容不合法：${first.path.join('.')} ${first.message}` : '备份内容不合法',
    )
  }

  const data = parsed.data
  return {
    app: BACKUP_APP,
    schemaVersion: BACKUP_SCHEMA_VERSION,
    exportedAt: data.exportedAt,
    problems: data.problems.map((p) => ({ ...p, tags: [] }) as Problem),
    tags: data.tags,
    lists: data.lists,
    listItems: data.listItems,
    reviews: data.reviews,
    settings: data.settings as AppSettings,
  }
}

const CSV_HEADER = [
  'id',
  'title',
  'difficulty',
  'status',
  'tags',
  'source',
  'url',
  'solveCount',
  'reviewStage',
  'solvedAt',
  'lastSolvedAt',
  'nextReviewAt',
  'createdAt',
] as const

function csvCell(value: unknown): string {
  const s = value == null ? '' : String(value)
  // CSV 里逗号、引号、换行都得包起来；双引号翻倍
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/**
 * CSV 导出：给人用 Excel 看的，所以时间写成 ISO 而不是 epoch 毫秒
 * （这是唯一一处不遵守「时间一律毫秒」的地方，因为它不是接口数据）。
 * 前面加 BOM，否则 Excel 打开中文是乱码。
 */
export function serializeCsv(problems: Problem[]): string {
  const lines = [CSV_HEADER.join(',')]
  for (const p of problems) {
    lines.push(
      [
        p.id,
        p.title,
        p.difficulty,
        p.status,
        p.tags.map((t) => t.name).join(' | '),
        p.source,
        p.url,
        p.solveCount,
        p.reviewStage,
        p.solvedAt ? dayjs(p.solvedAt).format('YYYY-MM-DD HH:mm') : '',
        p.lastSolvedAt ? dayjs(p.lastSolvedAt).format('YYYY-MM-DD HH:mm') : '',
        p.nextReviewAt ? dayjs(p.nextReviewAt).format('YYYY-MM-DD HH:mm') : '',
        p.createdAt ? dayjs(p.createdAt).format('YYYY-MM-DD HH:mm') : '',
      ]
        .map(csvCell)
        .join(','),
    )
  }
  return `\uFEFF${lines.join('\r\n')}`
}
