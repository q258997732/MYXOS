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
    <el-container>
      <el-header class="header">
        <span>{{ userStore.username }}</span>
        <el-button type="text" @click="logout">登出</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
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
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
  height: var(--header-height);
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.main {
  background-color: var(--content-bg);
  padding: var(--spacing-md);
}
</style>
