import { isDifficulty, isRating, REVIEW_KINDS } from '@/types/enums'
import type { Difficulty, ProblemStatus, Rating, ReviewKind } from '@/types/enums'
import type {
  AppSettings,
  ListItem,
  Problem,
  ReviewRecord,
  TagBrief,
} from '@/types/models'
import type {
  BackupPayload,
  DueReviewItem,
  DueReviewResult,
  ImportResult,
  ListProgress,
  MarkSolvedResult,
  OverviewStats,
  Paginated,
  RandomPickResult,
  RateResult,
  TagWithUsage,
} from '@/types/repository'
import type {
  BackupDto,
  DueDto,
  DueItemDto,
  ImportResultDto,
  ListDto,
  ListItemDto,
  OverviewDto,
  PageDto,
  ProblemDto,
  RandomPickDto,
  RateDto,
  ReviewRecordDto,
  SettingsDto,
  TagDto,
  TagUsageDto,
} from './dto'

/**
 * DTO → 前端模型的唯一出口。
 *
 * 枚举一律「非法值回落」而不是抛错：一条脏数据不该让整页列表 500。
 * 回落值都选最保守的那个（难度 medium、状态 todo、流水 REVIEW），
 * 这样坏数据最多是显示不对，不会把题误判成「已刷」而影响统计。
 */

function toDifficulty(v: string): Difficulty {
  return isDifficulty(v) ? v : 'medium'
}

function toStatus(v: string): ProblemStatus {
  return v === 'solved' ? 'solved' : 'todo'
}

function toReviewKind(v: string): ReviewKind {
  return (REVIEW_KINDS as readonly string[]).includes(v) ? (v as ReviewKind) : 'REVIEW'
}

function toRating(v: string | null): Rating | null {
  return v != null && isRating(v) ? v : null
}

export function toTagBrief(dto: TagDto): TagBrief {
  return { id: dto.id, name: dto.name, color: dto.color }
}

export function toProblem(dto: ProblemDto): Problem {
  return {
    id: dto.id,
    title: dto.title,
    difficulty: toDifficulty(dto.difficulty),
    status: toStatus(dto.status),
    // 后端 M8 起在 Converter 里把 NULL 归一成空串；这里再兜一次，
    // 让「连的是老版本后端」也不会把 null 漏进视图
    approach: dto.approach ?? '',
    note: dto.note ?? '',
    source: dto.source ?? '',
    url: dto.url ?? '',
    tagIds: dto.tagIds ?? [],
    tags: (dto.tags ?? []).map(toTagBrief),
    solvedAt: dto.solvedAt ?? null,
    lastSolvedAt: dto.lastSolvedAt ?? null,
    solveCount: dto.solveCount ?? 0,
    reviewStage: dto.reviewStage ?? 0,
    nextReviewAt: dto.nextReviewAt ?? null,
    lastReviewedAt: dto.lastReviewedAt ?? null,
    // 归档位只有 0/1 两种合法值，其它一律当未归档
    archived: dto.archived === 1 ? 1 : 0,
    createdAt: dto.createdAt ?? 0,
    updatedAt: dto.updatedAt ?? 0,
  }
}

export function toPaginated<TDto, T>(
  dto: PageDto<TDto>,
  map: (item: TDto) => T,
): Paginated<T> {
  const items = (dto.items ?? []).map(map)
  return {
    items,
    total: dto.total ?? 0,
    page: dto.page ?? 1,
    size: dto.size ?? items.length,
    // 前后端约定：空结果也是 1 页（后端 PageResult 在 M8 一并改成 max(1, ...)）
    totalPages: dto.totalPages ?? 1,
  }
}

export function toTagWithUsage(dto: TagUsageDto): TagWithUsage {
  return {
    id: dto.id,
    name: dto.name,
    color: dto.color,
    createdAt: dto.createdAt ?? 0,
    usageCount: dto.usageCount ?? 0,
  }
}

export function toListProgress(dto: ListDto): ListProgress {
  return {
    id: dto.id,
    name: dto.name,
    description: dto.description ?? '',
    color: dto.color,
    sortOrder: dto.sortOrder ?? 0,
    total: dto.total ?? 0,
    solved: dto.solved ?? 0,
  }
}

export function toListItem(dto: ListItemDto): ListItem {
  return {
    listId: dto.listId,
    problemId: dto.problemId,
    sortOrder: dto.sortOrder ?? 0,
    addedAt: dto.addedAt ?? 0,
  }
}

export function toReviewRecord(dto: ReviewRecordDto): ReviewRecord {
  return {
    id: dto.id,
    problemId: dto.problemId,
    kind: toReviewKind(dto.kind),
    rating: toRating(dto.rating),
    fromStage: dto.fromStage ?? null,
    toStage: dto.toStage ?? null,
    intervalDays: dto.intervalDays ?? null,
    nextReviewAt: dto.nextReviewAt ?? null,
    reviewedAt: dto.reviewedAt ?? 0,
    // 后端 review_record 表没有 note 列，前端也从没让用户填过，恒为空串
    note: dto.note ?? '',
  }
}

export function toDueItem(dto: DueItemDto): DueReviewItem {
  return {
    problem: toProblem(dto.problem),
    dueAt: dto.dueAt ?? 0,
    overdueDays: dto.overdueDays ?? 0,
    isOverdue: Boolean(dto.isOverdue),
  }
}

export function toDueResult(dto: DueDto): DueReviewResult {
  return {
    total: dto.total ?? 0,
    // limited 是「这次真的返回了几条」，与 total 是两个数
    limited: dto.limited ?? (dto.items ?? []).length,
    items: (dto.items ?? []).map(toDueItem),
  }
}

export function toRateResult(dto: RateDto): RateResult {
  return {
    problem: toProblem(dto.problem),
    fromStage: dto.fromStage ?? 0,
    toStage: dto.toStage ?? 0,
    intervalDays: dto.intervalDays ?? 0,
    nextReviewAt: dto.nextReviewAt ?? 0,
  }
}

export function toOverview(dto: OverviewDto): OverviewStats {
  return {
    dueCount: dto.dueCount ?? 0,
    solvedToday: dto.solvedToday ?? 0,
    reviewedToday: dto.reviewedToday ?? 0,
    totalProblems: dto.totalProblems ?? 0,
    solvedProblems: dto.solvedProblems ?? 0,
    listProgress: (dto.listProgress ?? []).map(toListProgress),
  }
}

export function toSettings(dto: SettingsDto): AppSettings {
  return {
    dailyReviewLimit: dto.dailyReviewLimit,
    pageSize: dto.pageSize,
    // 空数组会让复习算法取不到间隔，回落默认值
    reviewIntervals: dto.reviewIntervals?.length ? dto.reviewIntervals : [1, 3, 7, 15],
    overdueFromNow: Boolean(dto.overdueFromNow),
  }
}

export function toRandomPick(dto: RandomPickDto): RandomPickResult {
  return {
    problem: toProblem(dto.problem),
    // 只认 'all'，其余一律当未刷池——两个字面量之外的值没有意义
    pool: dto.pool === 'all' ? 'all' : 'unsolved',
    reset: Boolean(dto.reset),
    candidateCount: dto.candidateCount ?? 0,
  }
}

export function toMarkSolved(dto: { problem: ProblemDto; firstSolve: boolean; nextReviewAt: number | null }): MarkSolvedResult {
  return {
    problem: toProblem(dto.problem),
    firstSolve: Boolean(dto.firstSolve),
    nextReviewAt: dto.nextReviewAt ?? null,
  }
}

/** 备份文件：problems/lists 里带着进度、tags 是渲染快照，导入时由后端丢弃 */
export function toBackupPayload(dto: BackupDto): BackupPayload {
  return {
    app: 'dsa-log',
    schemaVersion: dto.schemaVersion,
    exportedAt: dto.exportedAt ?? 0,
    problems: (dto.problems ?? []).map(toProblem),
    tags: (dto.tags ?? []).map((t) => ({
      id: t.id,
      name: t.name,
      color: t.color,
      createdAt: t.createdAt ?? 0,
    })),
    // 题单要用完整字段（含 createdAt/updatedAt），ListProgress 少这两个
    lists: (dto.lists ?? []).map((l) => ({
      id: l.id,
      name: l.name,
      description: l.description ?? '',
      color: l.color,
      sortOrder: l.sortOrder ?? 0,
      createdAt: l.createdAt ?? 0,
      updatedAt: l.updatedAt ?? 0,
    })),
    listItems: (dto.listItems ?? []).map(toListItem),
    reviews: (dto.reviews ?? []).map(toReviewRecord),
    settings: toSettings(dto.settings),
  }
}

export function toImportResult(dto: ImportResultDto): ImportResult {
  return {
    mode: dto.mode === 'replace' ? 'replace' : 'merge',
    problems: { created: dto.problems?.created ?? 0, updated: dto.problems?.updated ?? 0, skipped: dto.problems?.skipped ?? 0 },
    tags: { created: dto.tags?.created ?? 0, updated: dto.tags?.updated ?? 0, skipped: dto.tags?.skipped ?? 0 },
    lists: { created: dto.lists?.created ?? 0, updated: dto.lists?.updated ?? 0, skipped: dto.lists?.skipped ?? 0 },
    listItems: dto.listItems ?? 0,
    reviews: dto.reviews ?? 0,
  }
}
