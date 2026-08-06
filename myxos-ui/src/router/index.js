import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store'
import LoginView from '@/views/LoginView.vue'
import Layout from '@/components/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardView.vue') },
      { path: 'devices', name: 'DeviceList', component: () => import('@/views/DeviceListView.vue') },
      { path: 'devices/create', name: 'DeviceCreate', component: () => import('@/views/DeviceCreateEditView.vue') },
      { path: 'devices/:id', name: 'DeviceDetail', component: () => import('@/views/DeviceDetailView.vue') },
      { path: 'devices/:id/edit', name: 'DeviceEdit', component: () => import('@/views/DeviceCreateEditView.vue') },
      { path: 'thresholds', name: 'ThresholdList', component: () => import('@/views/ThresholdListView.vue') },
      { path: 'thresholds/edit', name: 'ThresholdCreate', component: () => import('@/views/ThresholdEditView.vue') },
      { path: 'thresholds/edit/:id', name: 'ThresholdEdit', component: () => import('@/views/ThresholdEditView.vue') },
      { path: 'hosting', name: 'Hosting', component: () => import('@/views/HostingView.vue') },
      { path: 'alarms', name: 'AlarmList', component: () => import('@/views/AlarmListView.vue') },
      { path: 'logs', name: 'LogList', component: () => import('@/views/LogListView.vue') },
      { path: 'op-tasks', name: 'OpTaskList', component: () => import('@/views/OpTaskListView.vue') },
      { path: 'settings', name: 'Settings', component: () => import('@/views/SettingsView.vue') },
      { path: 'users', name: 'UserList', component: () => import('@/views/UserListView.vue') },
      { path: 'metric-catalogs', name: 'MetricCatalog', component: () => import('@/views/MetricCatalogView.vue'), meta: { admin: true } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    next('/login')
  } else if (to.matched.some(record => record.meta.admin) && !userStore.isAdmin) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
