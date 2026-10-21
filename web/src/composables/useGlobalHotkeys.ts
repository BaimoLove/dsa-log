import { onBeforeUnmount, onMounted } from 'vue'

export interface HotkeyHandlers {
  /** Ctrl/⌘+K 或 /：跳到题库并聚焦搜索框 */
  search: () => void
  /** R：随机一题 */
  random: () => void
  /** ?：快捷键说明 */
  help: () => void
}

/**
 * 在输入框里打字时不该触发单键快捷键——但 Ctrl+K 例外，
 * 浏览器默认的 Ctrl+K 是地址栏搜索，必须抢过来。
 */
export function isTypingTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el || typeof el.tagName !== 'string') return false
  if (el.isContentEditable === true) return true
  return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT'
}

export function isSearchHotkey(event: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey'>): boolean {
  return (
    (event.ctrlKey || event.metaKey) &&
    !event.altKey &&
    !event.shiftKey &&
    event.key.toLowerCase() === 'k'
  )
}

/** 有弹窗时不响应单键：R 会抽题、? 会再开一个弹窗，叠在一起很乱 */
function hasOpenDialog(): boolean {
  return typeof document !== 'undefined' && document.querySelector('[role="dialog"]') !== null
}

export function useGlobalHotkeys(handlers: HotkeyHandlers): void {
  function onKeydown(event: KeyboardEvent): void {
    // 输入法组字期间的按键是候选词交互，不是快捷键
    if (event.defaultPrevented || event.isComposing) return

    if (isSearchHotkey(event)) {
      // 抢掉浏览器默认的「聚焦地址栏」，但弹窗开着时不改变页面状态
      event.preventDefault()
      if (!hasOpenDialog()) handlers.search()
      return
    }

    if (isTypingTarget(event.target)) return
    if (event.ctrlKey || event.metaKey || event.altKey) return
    if (hasOpenDialog()) return

    if (event.key === 'r' || event.key === 'R') {
      event.preventDefault()
      handlers.random()
      return
    }
    if (event.key === '?' || event.key === '/') {
      event.preventDefault()
      if (event.key === '?') handlers.help()
      else handlers.search()
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
}
