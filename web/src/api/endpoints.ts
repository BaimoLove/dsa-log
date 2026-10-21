/**
 * 后端端点路径的唯一清单。仓储实现里不写裸字符串，
 * 改路径时只改这里，也不会出现某处少写 /api 前缀的碎片问题。
 *
 * 路径都相对 baseUrl（默认 '/api'），所以这里不再带 /api 前缀。
 */
export const ENDPOINTS = {
  problems: {
    list: '/problems',
    random: '/problems/random',
    detail: (id: string) => `/problems/${encodeURIComponent(id)}`,
    markSolved: (id: string) => `/problems/${encodeURIComponent(id)}/solved`,
    markUnsolved: (id: string) => `/problems/${encodeURIComponent(id)}/unsolved`,
    bulk: {
      solved: '/problems/bulk/solved',
      unsolved: '/problems/bulk/unsolved',
      tags: '/problems/bulk/tags',
      lists: '/problems/bulk/lists',
      remove: '/problems/bulk/delete',
    },
  },
  tags: {
    list: '/tags',
    detail: (id: string) => `/tags/${encodeURIComponent(id)}`,
  },
  lists: {
    list: '/lists',
    detail: (id: string) => `/lists/${encodeURIComponent(id)}`,
    problems: (id: string) => `/lists/${encodeURIComponent(id)}/problems`,
    problemIds: (id: string) => `/lists/${encodeURIComponent(id)}/problem-ids`,
    items: (id: string) => `/lists/${encodeURIComponent(id)}/items`,
    item: (id: string, problemId: string) =>
      `/lists/${encodeURIComponent(id)}/items/${encodeURIComponent(problemId)}`,
  },
  reviews: {
    due: '/reviews/due',
    rate: (problemId: string) => `/reviews/${encodeURIComponent(problemId)}/rate`,
    history: '/reviews',
  },
  stats: {
    overview: '/stats/overview',
  },
  settings: '/settings',
  export: '/export',
  import: '/import',
} as const
