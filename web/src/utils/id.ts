/** 主键统一用客户端 UUID：导出导入后 ID 不变，将来接后端可做幂等写入 */
export function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** multiEntry 索引要求数组内无重复值，否则 Dexie 抛 DataError */
export function uniqueIds(ids: readonly string[]): string[] {
  return Array.from(new Set(ids.filter(Boolean)))
}
