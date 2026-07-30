<template>
  <el-container class="layout">
    <el-aside width="200px" class="sidebar">
      <div class="logo">MYXOS</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/devices">
          <template #title>设备列表</template>
        </el-menu-item>
        <el-menu-item index="/thresholds">
          <template #title>阈值规则</template>
        </el-menu-item>
        <el-menu-item index="/hosting">
          <template #title>设备托管</template>
        </el-menu-item>
        <el-menu-item index="/alarms">
          <template #title>告警列表</template>
        </el-menu-item>
        <el-menu-item index="/logs">
          <template #title>日志查询</template>
        </el-menu-item>
        <el-menu-item index="/op-tasks">
          <template #title>任务队列</template>
        </el-menu-item>
        <el-menu-item index="/settings">
          <template #title>系统配置</template>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/users">
          <template #title>用户管理</template>
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
  background-color: #304156;
}
.logo {
  height: 60px;
  line-height: 60px;
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
  gap: 16px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.main {
  background-color: #f0f2f5;
}
</style>
