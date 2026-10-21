import type { Difficulty, ProblemStatus, SortOption } from './enums'
import type { Problem } from './models'

export interface Paginated<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

/** 批量操作只回报实际改动的条数；没有命中任何行时 affected=0 */
export interface BatchResult {
  affected: number
}

export interface MarkSolvedResult {
  problem: Problem
  firstSolve: boolean
  nextReviewAt: number | null
}

export interface ProblemQuery {
  keyword?: string
  difficulties?: Difficulty[]
  statuses?: ProblemStatus[]
  tagIds?: string[]
  listId?: string
  archived?: 0 | 1
  page?: number
  size?: number
  sort?: SortOption
}

export interface CreateProblemInput {
  title: string
  difficulty: Difficulty
  approach?: string
  note?: string
  source?: string
  url?: string
  tagIds?: string[]
}

export interface UpdateProblemInput {
  title?: string
  difficulty?: Difficulty
  approach?: string
  note?: string
  source?: string
  url?: string
  /** 不传 = 不改标签；传 [] = 清空标签 */
  tagIds?: string[]
  archived?: 0 | 1
}

/** 随机抽题结果：pool 说明这次用的是哪个池子，reset 说明本轮已抽完重新开始 */
export interface RandomPickResult {
  problem: Problem
  pool: 'unsolved' | 'all'
  reset: boolean
  candidateCount: number
}

export interface ProblemRepository {
  list(query: ProblemQuery): Promise<Paginated<Problem>>
  getById(id: string): Promise<Problem | null>
  create(input: CreateProblemInput): Promise<Problem>
  update(id: string, patch: UpdateProblemInput): Promise<Problem>
  remove(id: string): Promise<void>
  markSolved(id: string): Promise<MarkSolvedResult>
  markUnsolved(id: string): Promise<Problem>
  bulkMarkSolved(ids: string[]): Promise<BatchResult>
  /** 批量取消已刷：状态的相反操作，替代「撤销」。保留 solvedAt / solveCount 历史 */
  bulkMarkUnsolved(ids: string[]): Promise<BatchResult>
  bulkAddTags(ids: string[], tagIds: string[]): Promise<BatchResult>
  bulkAddToList(ids: string[], listId: string): Promise<BatchResult>
  bulkRemove(ids: string[]): Promise<BatchResult>
  /** 抽一题：未刷优先 → 未刷重置 → 全库 → 全库重置；题库为空返回 null */
  random(excludeIds: string[]): Promise<RandomPickResult | null>
}

export interface TagRepository {
  list(keyword?: string): Promise<TagWithUsage[]>
  create(name: string, color: string): Promise<TagBriefLike>
  update(id: string, patch: { name?: string; color?: string }): Promise<TagBriefLike>
  remove(id: string): Promise<void>
}

export interface TagBasic {
  id: string
  name: string
  color: string
}

export type TagWithUsage = TagBasic & { createdAt: number; usageCount: number }
export type TagBriefLike = TagBasic

export interface ListProgress extends ProblemListBasic {
  total: number
  solved: number
}

export interface ProblemListBasic {
  id: string
  name: string
  description: string
  color: string
  sortOrder: number
}

export interface ListRepository {
  list(): Promise<ListProgress[]>
  getById(id: string): Promise<ProblemListBasic | null>
  create(input: { name: string; description?: string; color?: string }): Promise<ProblemListBasic>
  update(
    id: string,
    patch: { name?: string; description?: string; color?: string },
  ): Promise<ProblemListBasic>
  remove(id: string): Promise<void>
  addProblems(listId: string, problemIds: string[]): Promise<BatchResult>
  removeProblem(listId: string, problemId: string): Promise<void>
  problemIdsOf(listId: string): Promise<string[]>
}

export interface DueReviewItem {
  problem: Problem
  dueAt: number
  overdueDays: number
  isOverdue: boolean
}

export interface DueReviewResult {
  total: number
  limited: number
  items: DueReviewItem[]
}

export interface RateResult {
  problem: Problem
  fromStage: number
  toStage: number
  intervalDays: number
  nextReviewAt: number
}

export interface ReviewRepository {
  due(limit?: number): Promise<DueReviewResult>
  rate(problemId: string, rating: import('./enums').Rating): Promise<RateResult>
  history(problemId: string): Promise<import('./models').ReviewRecord[]>
}

export interface SettingsRepository {
  get(): Promise<import('./models').AppSettings>
  save(patch: Partial<import('./models').AppSettings>): Promise<import('./models').AppSettings>
}

export interface OverviewStats {
  dueCount: number
  solvedToday: number
  reviewedToday: number
  totalProblems: number
  solvedProblems: number
  listProgress: ListProgress[]
}

export interface StatsRepository {
  overview(): Promise<OverviewStats>
}

/** 全量备份的顶层形状。schemaVersion 不匹配一律拒绝，不做「尽力而为」的兼容 */
export interface BackupPayload {
  app: 'dsa-log'
  schemaVersion: number
  exportedAt: number
  problems: import('./models').Problem[]
  tags: import('./models').Tag[]
  lists: import('./models').ProblemList[]
  listItems: import('./models').ListItem[]
  reviews: import('./models').ReviewRecord[]
  settings: import('./models').AppSettings
}

export type ImportMode = 'merge' | 'replace'

export interface ImportCounts {
  created: number
  updated: number
  skipped: number
}

export interface ImportResult {
  mode: ImportMode
  problems: ImportCounts
  tags: ImportCounts
  lists: ImportCounts
  listItems: number
  reviews: number
}

export interface BackupRepository {
  /** 全量导出：六张表一起，供「导出 → 换个浏览器导入」整库还原 */
  exportAll(): Promise<BackupPayload>
  /** CSV 是给人看的（Excel 打开），时间用 ISO，不复用 epoch 毫秒约定 */
  exportCsv(): Promise<string>
  /**
   * merge：按 id 合并，冲突时以 updatedAt 新的一方为准，同 id 同时间算 skipped
   * replace：先清空六张表再写，调用方必须先做二次确认
   */
  importAll(payload: BackupPayload, mode: ImportMode): Promise<ImportResult>
}
