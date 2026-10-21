export const DIFFICULTIES = ['easy', 'medium', 'hard'] as const
export type Difficulty = (typeof DIFFICULTIES)[number]

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  easy: '简单',
  medium: '中等',
  hard: '困难',
}

/** 难度排序权重，用于「难度从低到高」的展示，避免依赖字符串比较 */
export const DIFFICULTY_WEIGHT: Record<Difficulty, number> = {
  easy: 1,
  medium: 2,
  hard: 3,
}

export const STATUSES = ['todo', 'solved'] as const
export type ProblemStatus = (typeof STATUSES)[number]

export const STATUS_LABEL: Record<ProblemStatus, string> = {
  todo: '未刷',
  solved: '已刷',
}

/** 复习评分三档：忘了 / 模糊 / 记得 */
export const RATINGS = ['again', 'hard', 'good'] as const
export type Rating = (typeof RATINGS)[number]

export const RATING_LABEL: Record<Rating, string> = {
  again: '忘了',
  hard: '模糊',
  good: '记得',
}

/** 复习流水类型：标记已刷 / 取消已刷 / 复习评分 */
export const REVIEW_KINDS = ['MARK_SOLVED', 'MARK_UNSOLVED', 'REVIEW'] as const
export type ReviewKind = (typeof REVIEW_KINDS)[number]

export const SORT_OPTIONS = [
  'updatedAt_desc',
  'updatedAt_asc',
  'createdAt_desc',
  'createdAt_asc',
  'difficulty_asc',
  'difficulty_desc',
  'nextReviewAt_asc',
  'solvedAt_desc',
] as const
export type SortOption = (typeof SORT_OPTIONS)[number]

export const SORT_LABEL: Record<SortOption, string> = {
  updatedAt_desc: '最近更新',
  updatedAt_asc: '最早更新',
  createdAt_desc: '最新创建',
  createdAt_asc: '最早创建',
  difficulty_asc: '难度从低到高',
  difficulty_desc: '难度从高到低',
  nextReviewAt_asc: '最近待复习',
  solvedAt_desc: '最近已刷',
}

export function isDifficulty(v: unknown): v is Difficulty {
  return typeof v === 'string' && (DIFFICULTIES as readonly string[]).includes(v)
}

export function isRating(v: unknown): v is Rating {
  return typeof v === 'string' && (RATINGS as readonly string[]).includes(v)
}
