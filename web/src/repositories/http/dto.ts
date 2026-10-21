/**
 * 后端出参的原始形状（DTO）。
 *
 * 为什么不直接 `request<Problem>`：后端的 VO 与前端模型有三处结构性差异，
 * 让它们显式出现在类型里，映射处就被编译器盯着：
 *
 * 1. 文本字段（approach/note/source/url）在库里是 NULL，后端从 M8 起统一转空串，
 *    但 DTO 仍按 `string | null` 声明——老版本后端或直接读库的脚本还是会给出 null。
 * 2. 枚举（difficulty/status/kind/rating）是裸 string，不是字面量联合。
 * 3. 计数类字段（solveCount/reviewStage）后端是 Integer，理论上可为 null。
 *
 * 视图层永远看不到这些类型，只有 repositories/http 内部使用。
 */

export interface TagDto {
  id: string
  name: string
  color: string
}

export interface ProblemDto {
  id: string
  title: string
  difficulty: string
  status: string
  approach: string | null
  note: string | null
  source: string | null
  url: string | null
  solvedAt: number | null
  lastSolvedAt: number | null
  solveCount: number | null
  reviewStage: number | null
  nextReviewAt: number | null
  lastReviewedAt: number | null
  createdAt: number | null
  updatedAt: number | null
  archived: number | null
  tagIds: string[]
  tags: TagDto[]
}

export interface PageDto<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface TagUsageDto {
  id: string
  name: string
  color: string
  createdAt: number | null
  usageCount: number
}

export interface ListDto {
  id: string
  name: string
  description: string | null
  color: string
  sortOrder: number | null
  createdAt: number | null
  updatedAt: number | null
  total: number
  solved: number
}

export interface ListItemDto {
  listId: string
  problemId: string
  sortOrder: number | null
  addedAt: number | null
}

export interface ReviewRecordDto {
  id: string
  problemId: string
  kind: string
  rating: string | null
  fromStage: number | null
  toStage: number | null
  intervalDays: number | null
  nextReviewAt: number | null
  reviewedAt: number | null
  /** 后端表里没有这一列，前端模型有；mapper 补空串 */
  note?: string
}

export interface DueItemDto {
  problem: ProblemDto
  dueAt: number
  overdueDays: number
  /** 注意字段名是 isOverdue 而不是 overdue：后端特意用 Boolean 包装类型绕开 Lombok 的 is 前缀 */
  isOverdue: boolean
}

export interface DueDto {
  total: number
  limited: number
  items: DueItemDto[]
}

export interface RateDto {
  problem: ProblemDto
  record: ReviewRecordDto
  fromStage: number
  toStage: number
  intervalDays: number
  nextReviewAt: number
}

export interface OverviewDto {
  dueCount: number
  solvedToday: number
  reviewedToday: number
  totalProblems: number
  solvedProblems: number
  listProgress: ListDto[]
}

export interface SettingsDto {
  dailyReviewLimit: number
  pageSize: number
  reviewIntervals: number[]
  overdueFromNow: boolean
}

export interface RandomPickDto {
  problem: ProblemDto
  /** unsolved | all */
  pool: string
  reset: boolean
  candidateCount: number
}

export interface MarkSolvedDto {
  problem: ProblemDto
  firstSolve: boolean
  nextReviewAt: number | null
}

export interface BatchDto {
  affected: number
}

export interface ImportCountsDto {
  created: number
  updated: number
  skipped: number
}

export interface ImportResultDto {
  mode: string
  problems: ImportCountsDto
  tags: ImportCountsDto
  lists: ImportCountsDto
  listItems: number
  reviews: number
}

export interface BackupDto {
  app: string
  schemaVersion: number
  exportedAt: number
  problems: ProblemDto[]
  tags: TagUsageDto[]
  lists: ListDto[]
  listItems: ListItemDto[]
  reviews: ReviewRecordDto[]
  settings: SettingsDto
}
