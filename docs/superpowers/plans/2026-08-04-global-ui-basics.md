# 全局 UI 基础改进实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构顶部导航区域、统一时间展示格式、消除页面内容区顶部白边，为设备托管/详情/任务队列等页面提供一致的基础体验。

**Architecture:** 将 `Layout.vue` 的 `el-header` 移除，把用户名和登出按钮下沉到每个页面的标题栏右侧；新增 `date.js` 工具函数统一格式化所有时间字段；调整 `style.css` 中 `.page-container` 和 `.page-title` 的间距，使页面与侧边栏视觉对齐。

**Tech Stack:** Vue 3, Element Plus, myxos-ui (Vite)

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myxos-ui/src/components/Layout.vue` | 移除顶部白色 header，侧边栏 + 主内容区直接撑满 |
| `myxos-ui/src/utils/date.js` | 统一时间格式化函数 `formatDateTime` |
| `myxos-ui/src/style.css` | 调整 `.page-container` / `.page-title` 间距，新增 `.page-header` 工具类 |
| `myxos-ui/src/views/*` | 各视图标题栏引入 `.page-header` 并放置用户/登出（本计划中只改 Layout 与样式，具体页面在各自计划中改） |

---

### Task 1: 新增时间格式化工具

**Files:**
- Create: `myxos-ui/src/utils/date.js`
- Test: 手动在任意视图 `onMounted` 中打印 `formatDateTime(new Date())`

- [ ] **Step 1: 创建 `date.js`**

```javascript
import dayjs from 'dayjs'

const DEFAULT_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/**
 * 将日期格式化为 YYYY-MM-DD HH:mm:ss
 * @param {string|number|Date} value
 * @param {string} [format]
 * @returns {string} 格式化后字符串，非法输入返回 '-'
 */
export function formatDateTime(value, format = DEFAULT_FORMAT) {
  if (!value || value === '-') {
    return '-'
  }
  const d = dayjs(value)
  if (!d.isValid()) {
    return '-'
  }
  return d.format(format)
}

/**
 * 判断值是否可被 dayjs 解析
 * @param {*} value
 * @returns {boolean}
 */
export function isValidDate(value) {
  return dayjs(value).isValid()
}
```

- [ ] **Step 2: 在 `main.js` 全局挂载方便模板使用（可选）**

```javascript
// myxos-ui/src/main.js
import { formatDateTime } from '@/utils/date'

app.config.globalProperties.$formatDateTime = formatDateTime
```

- [ ] **Step 3: Commit**

```bash
git add myxos-ui/src/utils/date.js myxos-ui/src/main.js
git commit -m "feat(ui): 新增统一时间格式化工具"
```

---

### Task 2: 重构 Layout 去掉顶部白边

**Files:**
- Modify: `myxos-ui/src/components/Layout.vue`
- Modify: `myxos-ui/src/style.css`

- [ ] **Step 1: 修改 `Layout.vue`**

移除 `el-header`，把用户信息/登出从全局 header 移除；主内容区直接由 `el-main` 承载。

```vue
<template>
  <el-container class="layout">
    <el-aside class="sidebar">
      <div class="logo">MYXOS</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Monitor /></el-icon>
          <span>设备列表</span>
        </el-menu-item>
        <el-menu-item index="/thresholds">
          <el-icon><Bell /></el-icon>
          <span>阈值规则</span>
        </el-menu-item>
        <el-menu-item index="/hosting">
          <el-icon><Connection /></el-icon>
          <span>设备托管</span>
        </el-menu-item>
        <el-menu-item index="/alarms">
          <el-icon><Warning /></el-icon>
          <span>告警列表</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <span>日志查询</span>
        </el-menu-item>
        <el-menu-item index="/op-tasks">
          <el-icon><List /></el-icon>
          <span>任务队列</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store'
import { authApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

async function logout() {
  try {
    await authApi.logout()
  } finally {
    userStore.clearUser()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.sidebar {
  width: var(--sidebar-width) !important;
  background-color: var(--sidebar-bg);
}
.logo {
  height: var(--header-height);
  line-height: var(--header-height);
  text-align: center;
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
}
.main {
  background-color: var(--content-bg);
  padding: 0;
  overflow: auto;
}
</style>
```

- [ ] **Step 2: 在 `style.css` 中新增 `.page-header` 工具类并调整 `.page-container/.page-title`**

```css
/* myxos-ui/src/style.css */
.page-container {
  padding: var(--spacing-md);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: 14px;
}
```

- [ ] **Step 3: Commit**

```bash
git add myxos-ui/src/components/Layout.vue myxos-ui/src/style.css
git commit -m "feat(ui): 移除全局顶部白边，标题栏承载页面操作区"
```

---

## Self-Review

1. **Spec coverage:**
   - "每个页面右侧内容区域上面都白了一块很难看，应该将白色部分去掉" → Task 2 移除 `el-header`。
   - "右上角的用户与登出与页面标题放同一区域内，标题在左侧，登出与用户在右侧" → Task 2 移除全局 header，后续各视图计划使用 `.page-header` 放置标题与用户/登出。
   - "所有的前端显示时间都采用 YYYY-MM-DD HH:mm:ss" → Task 1 提供统一工具，后续视图计划逐步替换。

2. **Placeholder scan:** 无 TBD/TODO，所有步骤含代码。

3. **Type consistency:** `formatDateTime` 返回 string，`-` 兜底；CSS 变量沿用现有 `variables.css`。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-04-global-ui-basics.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task.
2. **Inline Execution** - execute tasks in this session using executing-plans.

This plan should be implemented **before** the device-hosting, device-detail, and task-queue plans because they depend on the shared `.page-header` style and `formatDateTime` utility.
