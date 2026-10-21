import type { Difficulty, ProblemStatus, SortOption } from './enums'
import type { Problem } from './models'

export interface Paginated<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface BatchResult {
  affected: number
  /** 撤销令牌；为空表示本次不可撤销（例如服务端快照写入失败） */
  opId?: string
  /** 撤销截止时间（UTC 毫秒），前端倒计时以此为准，不信任本地 +6000 */
  undoExpiresAt?: number
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
  bulkAddTags(ids: string[], tagIds: string[]): Promise<BatchResult>
  bulkAddToList(ids: string[], listId: string): Promise<BatchResult>
  bulkRemove(ids: string[]): Promise<BatchResult>
  /** 抽一题：未刷优先 → 未刷重置 → 全库 → 全库重置；题库为空返回 null */
  random(excludeIds: string[]): Promise<RandomPickResult | null>
  upsertMany(items: Problem[]): Promise<{ created: number; updated: number; skipped: number }>
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

export interface UndoRepository {
  undo(opId: string): Promise<{ affected: number }>
}
