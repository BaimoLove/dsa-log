import { createRouter, createWebHashHistory } from 'vue-router'
import { routes } from './routes'

/**
 * hash 模式：部署到任意子路径/目录都不需要 nginx rewrite。
 * 上线后首次打开是空库时，先导入一份备份，或从题库页手动建题。
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
