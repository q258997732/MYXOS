<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">任务队列</h2>
      <div class="page-actions">
        <span>{{ userStore.username }}</span>
        <el-button type="primary" link @click="logout">登出</el-button>
      </div>
    </div>

    <div class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待执行" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.source" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="手动" value="MANUAL" />
            <el-option label="自动" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="tasks" size="small" stripe>
        <el-table-column prop="id" label="任务ID" width="90" />
        <el-table-column prop="deviceId" label="设备ID" width="90" />
        <el-table-column prop="operationCode" label="操作" width="150" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="90" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduledAt" label="计划时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.scheduledAt) }}</template>
        </el-table-column>
        <el-table-column prop="finishedAt" label="完成时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="resultMsg" label="结果" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'FAILED'" size="small" :icon="RefreshRight" @click="retry(row.id)">重试</el-button>
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

    <el-drawer v-model="detailVisible" title="任务详情" size="560px">
      <el-descriptions v-if="currentTask" :column="1" border size="small">
        <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
        <el-descriptions-item label="设备ID">{{ currentTask.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentTask.operationCode }}</el-descriptions-item>
        <el-descriptions-item label="参数">
          <pre class="json-preview">{{ formatJson(currentTask.params) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(currentTask.status)" size="small">{{ currentTask.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ currentTask.retryCount }} / {{ currentTask.maxRetry }}</el-descriptions-item>
        <el-descriptions-item label="计划时间">{{ formatDateTime(currentTask.scheduledAt) }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDateTime(currentTask.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDateTime(currentTask.finishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="执行结果">
          <pre class="json-preview">{{ currentTask.resultMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, RefreshRight } from '@element-plus/icons-vue'
import { opTaskApi, authApi } from '@/api'
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

const tasks = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: '', source: '', page: 1, size: 20 })

const detailVisible = ref(false)
const currentTask = ref(null)

const statusType = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await opTaskApi.list(query)
    tasks.splice(0, tasks.length, ...(res.data.records || []))
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
  query.status = ''
  query.source = ''
  query.page = 1
  load()
}

const retry = async (id) => {
  await opTaskApi.retry(id)
  ElMessage.success('已重试')
  load()
}

const openDetail = async (row) => {
  try {
    const res = await opTaskApi.detail(row.id)
    currentTask.value = res.data
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
}

function formatJson(json) {
  if (!json) return '-'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch (e) {
    return json
  }
}

onMounted(load)
</script>
