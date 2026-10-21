import type { RouteRecordRaw } from 'vue-router'

/**
 * 页面清单（与 docs/PLAN.md 第五节一致）。
 * meta.title 用于侧栏与浏览器标题；meta.icon 对应 lucide 图标名，由 SideBar 映射。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '概览', icon: 'LayoutDashboard' },
  },
  {
    path: '/problems',
    name: 'problems',
    component: () => import('@/views/ProblemListView.vue'),
    meta: { title: '题目', icon: 'ListChecks' },
  },
  {
    path: '/problems/new',
    name: 'problem-new',
    component: () => import('@/views/ProblemEditView.vue'),
    meta: { title: '新建题目', hidden: true },
  },
  {
    path: '/problems/:id',
    name: 'problem-detail',
    component: () => import('@/views/ProblemDetailView.vue'),
    meta: { title: '题目详情', hidden: true },
  },
  {
    path: '/problems/:id/edit',
    name: 'problem-edit',
    component: () => import('@/views/ProblemEditView.vue'),
    meta: { title: '编辑题目', hidden: true },
  },
  {
    path: '/lists',
    name: 'lists',
    component: () => import('@/views/ListBoardView.vue'),
    meta: { title: '题单', icon: 'Layers' },
  },
  {
    path: '/lists/:id',
    name: 'list-detail',
    component: () => import('@/views/ListDetailView.vue'),
    meta: { title: '题单详情', hidden: true },
  },
  {
    path: '/tags',
    name: 'tags',
    component: () => import('@/views/TagManageView.vue'),
    meta: { title: '标签', icon: 'Tags' },
  },
  {
    path: '/review',
    name: 'review',
    component: () => import('@/views/ReviewView.vue'),
    meta: { title: '复习', icon: 'RotateCcw' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '设置', icon: 'Settings' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
]

export const navRoutes = routes.filter((r) => !r.meta?.hidden)
