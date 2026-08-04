<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">日志查询</h2>
      <div class="page-actions">
        <span>{{ userStore.username }}</span>
        <el-button type="primary" link @click="logout">登出</el-button>
      </div>
    </div>

    <div class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="动作类型">
          <el-select v-model="query.actionType" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="日志" value="LOG" />
            <el-option label="操作" value="OPERATION" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="query.logLevel" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="logs" size="small" stripe>
        <el-table-column prop="id" label="日志ID" width="90" />
        <el-table-column prop="deviceId" label="设备ID" width="90" />
        <el-table-column prop="taskId" label="任务ID" width="90" />
        <el-table-column prop="actionType" label="动作类型" width="100" />
        <el-table-column prop="logLevel" label="日志级别" width="90">
          <template #default="{ row }">
            <el-tag :type="logLevelType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @change="load"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="日志详情" width="560px" align-center destroy-on-close>
      <el-descriptions v-if="currentLog" :column="1" border size="small">
        <el-descriptions-item label="日志ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="设备ID">{{ currentLog.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="任务ID">{{ currentLog.taskId }}</el-descriptions-item>
        <el-descriptions-item label="动作类型">{{ currentLog.actionType }}</el-descriptions-item>
        <el-descriptions-item label="日志级别">
          <el-tag :type="logLevelType(currentLog.logLevel)" size="small">{{ currentLog.logLevel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDateTime(currentLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="详细内容">
          <pre class="log-detail">{{ currentLog.message }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { logApi, authApi } from '@/api'
import { useUserStore } from '@/store'
import { formatDateTime } from '@/utils/date'

const router = useRouter()
const userStore = useUserStore()

const logout = async () => {
  try {
    await authApi.logout()
  } catch (e) {
    // ignore
  }
  userStore.clearUser()
  router.push('/login')
}

const logs = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ actionType: '', logLevel: '', page: 1, size: 20 })

const detailVisible = ref(false)
const currentLog = ref(null)

const logLevelType = (level) => {
  switch (level) {
    case 'ERROR': return 'danger'
    case 'WARN': return 'warning'
    case 'INFO': return 'success'
    default: return 'info'
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await logApi.list(query)
    logs.splice(0, logs.length, ...(res.data.records || []))
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.page = 1
  load()
}

const reset = () => {
  query.actionType = ''
  query.logLevel = ''
  query.page = 1
  load()
}

const openDetail = (row) => {
  currentLog.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.log-detail {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  background-color: #f5f7fa;
  padding: var(--spacing-sm);
  border-radius: var(--border-radius);
}
</style>
