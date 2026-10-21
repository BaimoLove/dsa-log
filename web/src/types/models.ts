import type { Difficulty, ProblemStatus, Rating, ReviewKind } from './enums'

/** 标签精简视图，随题目一起下发，避免列表页二次请求 */
export interface TagBrief {
  id: string
  name: string
  color: string
}

export interface Tag extends TagBrief {
  createdAt: number
}

/**
 * 题目聚合根。
 *
 * 注意两点：
 * 1. 所有时间字段统一为 UTC 毫秒时间戳（number），不用字符串，避免时区歧义。
 * 2. **不存题干原文**——这里只记题名、来源链接和自己写的东西，原文去外链看。
 */
export interface Problem {
  id: string
  title: string
  difficulty: Difficulty
  status: ProblemStatus
  /** 解题思路 / 代码（自己写的，不是抄题干） */
  approach: string
  /** 复盘备注、踩坑 */
  note: string
  /** 来源：LeetCode / 牛客 / 笔试 ... */
  source: string
  /** 题目外链，不存原文 */
  url: string
  tagIds: string[]
  /** 随题下发的标签详情，来自关联查询，不是表字段 */
  tags: TagBrief[]
  /** 首次标记已刷时间，只写一次 */
  solvedAt: number | null
  /** 最近一次标记已刷时间 */
  lastSolvedAt: number | null
  /** 累计标记已刷次数 */
  solveCount: number
  /** 复习阶段 0-4，4 = 毕业 */
  reviewStage: number
  /** 下次复习时间，null = 不在复习池 */
  nextReviewAt: number | null
  lastReviewedAt: number | null
  archived: 0 | 1
  createdAt: number
  updatedAt: number
}

export interface ProblemList {
  id: string
  name: string
  description: string
  color: string
  sortOrder: number
  createdAt: number
  updatedAt: number
}

export interface ListItem {
  listId: string
  problemId: string
  sortOrder: number
  addedAt: number
}

export interface ReviewRecord {
  id: string
  problemId: string
  kind: ReviewKind
  rating: Rating | null
  fromStage: number | null
  toStage: number | null
  intervalDays: number | null
  nextReviewAt: number | null
  reviewedAt: number
  note: string
}

export interface AppSettings {
  dailyReviewLimit: number
  pageSize: number
  reviewIntervals: number[]
  overdueFromNow: boolean
}

export const DEFAULT_SETTINGS: AppSettings = {
  dailyReviewLimit: 20,
  pageSize: 20,
  reviewIntervals: [1, 3, 7, 15],
  overdueFromNow: true,
}
