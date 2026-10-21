import { createRouter, createWebHashHistory } from 'vue-router'
import { routes } from './routes'

/**
 * hash 模式：部署到任意子路径/目录都不需要 nginx rewrite。
 * 上线后首次打开是空库，按 docs/PLAN.md 第二章做一次数据迁移。
 */
export const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) ?? ''
  document.title = title ? `${title} · 刷题记录` : '刷题记录'
})
