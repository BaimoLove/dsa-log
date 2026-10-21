import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

export { dayjs }

export const DAY_MS = 24 * 60 * 60 * 1000

/** 本地时区的今日 00:00:00.000，用于「今天到期」「今日已刷」的边界判定 */
export function todayStart(now: number = Date.now()): number {
  return dayjs(now).startOf('day').valueOf()
}

/** 本地时区的次日 00:00:00.000（左闭右开区间的右端） */
export function tomorrowStart(now: number = Date.now()): number {
  return dayjs(now).add(1, 'day').startOf('day').valueOf()
}

export function addDays(ms: number, days: number): number {
  return ms + days * DAY_MS
}

/** 与 dueAt 相差的自然日天数，正数代表已逾期 */
export function overdueDays(dueAt: number, now: number = Date.now()): number {
  return dayjs(now).startOf('day').diff(dayjs(dueAt).startOf('day'), 'day')
}

export function formatDate(ms: number | null | undefined): string {
  return ms ? dayjs(ms).format('YYYY-MM-DD') : '—'
}

export function formatDateTime(ms: number | null | undefined): string {
  return ms ? dayjs(ms).format('YYYY-MM-DD HH:mm') : '—'
}

export function fromNow(ms: number | null | undefined): string {
  return ms ? dayjs(ms).fromNow() : '—'
}
