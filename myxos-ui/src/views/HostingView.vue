<template>
  <div class="page-container">
    <h2 class="page-title">设备托管</h2>

    <el-row :gutter="16" class="hosting-row">
      <el-col :xs="24" :lg="12" class="hosting-col">
        <el-card class="hosting-card" shadow="never">
          <template #header>
            <div class="card-header"><span>手动添加</span></div>
          </template>
          <el-form :model="manualForm" label-width="100px">
            <el-form-item label="IP">
              <el-input v-model="manualForm.ip" placeholder="例如 192.168.1.10" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="manualForm.port" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
            <el-form-item label="模式">
              <el-select v-model="manualForm.mode" style="width: 100%">
                <el-option label="桥接" value="BRIDGE" />
                <el-option label="NAT" value="NAT" />
              </el-select>
            </el-form-item>
            <el-form-item label="分组">
              <el-select v-model="manualForm.groupId" clearable placeholder="请选择分组" style="width: 100%">
                <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="manualForm.name" placeholder="留空自动读取" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Plus" @click="createDevice" :loading="manualSaving">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12" class="hosting-col">
        <el-card class="hosting-card discover-card" shadow="never">
          <template #header>
            <div class="card-header"><span>网段发现</span></div>
          </template>
          <el-form :model="discoverForm" label-width="100px">
            <el-form-item label="CIDR">
              <el-input v-model="discoverForm.cidr" placeholder="192.168.30.0/24" />
            </el-form-item>
            <el-form-item label="起始端口">
              <el-input-number v-model="discoverForm.portFrom" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束端口">
              <el-input-number v-model="discoverForm.portTo" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="submitDiscover" :loading="discoverSubmitting">开始扫描</el-button>
            </el-form-item>
          </el-form>

          <!-- 发现进度条 -->
          <div v-if="runningTask" class="discover-progress">
            <el-divider content-position="left">发现进度</el-divider>
            <div class="progress-info">
              <span>{{ runningTask.cidr }}</span>
              <span class="progress-count">{{ runningTask.scannedIpCount }} / {{ runningTask.totalIpCount }}</span>
            </div>
            <el-progress
              :percentage="progressPercent(runningTask)"
              :status="runningTask.status === 'RUNNING' ? '' : 'success'"
              :stroke-width="18"
              striped
              striped-flow
            />
            <div class="progress-status">
              <el-tag :type="statusType(runningTask.status)" size="small">{{ runningTask.status }}</el-tag>
              <span class="progress-message">{{ runningTask.message }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="content-card mt-16" shadow="never">
      <template #header>
        <div class="card-header">
          <span>发现任务</span>
          <el-button type="danger" link size="small" :icon="Delete" @click="clearAll">清空已完成</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" size="small" stripe
>
        <el-table-column prop="cidr" label="CIDR" />
        <el-table-column prop="portFrom" label="起始端口" width="100" />
        <el-table-column prop="portTo" label="结束端口" width="100" />
        <el-table-column label="进度" width="240">
          <template #default="{ row }">
            <el-progress :percentage="progressPercent(row)" :stroke-width="12" />
            <div class="table-progress-text">{{ row.scannedIpCount }} / {{ row.totalIpCount }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="foundCount" label="发现数量" width="100" />
        <el-table-column prop="startedAt" label="开始时间" width="160" />
        <el-table-column prop="finishedAt" label="完成时间" width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="remove(row.id)">删除</el-button>
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
          @change="loadTasks"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import { deviceApi, deviceGroupApi, discoverApi } from '@/api'

const groups = reactive([])
const tasks = reactive([])
const total = ref(0)
const loading = ref(false)
const manualSaving = ref(false)
const discoverSubmitting = ref(false)
const query = reactive({ page: 1, size: 20 })
const refreshTimer = ref(null)

const manualForm = reactive({ ip: '', port: 9082, mode: 'BRIDGE', groupId: null, name: '' })
const discoverForm = reactive({ cidr: '', portFrom: 9082, portTo: 9082 })

const runningTask = computed(() => {
  return tasks.find(t => t.status === 'RUNNING' || t.status === 'PENDING') || null
})

const statusType = (status) => {
  switch (status) {
    case 'DONE': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const progressPercent = (row) => {
  if (!row || !row.totalIpCount) return 0
  const percent = Math.round((row.scannedIpCount / row.totalIpCount) * 100)
  return Math.min(100, Math.max(0, percent))
}

const loadGroups = async () => {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...(res.data || []))
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await discoverApi.tasks(query)
    tasks.splice(0, tasks.length, ...(res.data.records || []))
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const createDevice = async () => {
  manualSaving.value = true
  try {
    await deviceApi.create(manualForm)
    ElMessage.success('设备已添加')
    manualForm.ip = ''
    manualForm.name = ''
  } finally {
    manualSaving.value = false
  }
}

const submitDiscover = async () => {
  discoverSubmitting.value = true
  try {
    await discoverApi.scan(discoverForm)
    ElMessage.success('扫描任务已提交')
    loadTasks()
    startRefresh()
  } finally {
    discoverSubmitting.value = false
  }
}

const startRefresh = () => {
  if (refreshTimer.value) return
  refreshTimer.value = setInterval(() => {
    loadTasks()
    if (!runningTask.value) {
      stopRefresh()
    }
  }, 3000)
}

const stopRefresh = () => {
  if (refreshTimer.value) {
    clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
}

const remove = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该发现任务？', '提示', { type: 'warning' })
    await discoverApi.deleteTask(id)
    ElMessage.success('删除成功')
    loadTasks()
  } catch (e) {}
}

const clearAll = async () => {
  try {
    await ElMessageBox.confirm('确认清空所有已完成/失败的任务？', '提示', { type: 'warning' })
    await discoverApi.clearTasks()
    ElMessage.success('清空成功')
    loadTasks()
  } catch (e) {}
}

onMounted(() => {
  loadGroups()
  loadTasks()
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
})
</script>

<style scoped>
.hosting-row {
  align-items: stretch;
}
.hosting-col {
  margin-bottom: var(--spacing-md);
}
.hosting-card {
  height: 100%;
}
.discover-card {
  display: flex;
  flex-direction: column;
}
.discover-progress {
  margin-top: auto;
  padding-top: var(--spacing-md);
}
.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-xs);
  font-size: 13px;
  color: var(--text-secondary);
}
.progress-count {
  font-family: 'Courier New', monospace;
}
.progress-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-xs);
}
.progress-message {
  font-size: 13px;
  color: var(--text-secondary);
}
.table-progress-text {
  font-size: 12px;
  color: var(--text-muted);
  text-align: right;
  margin-top: 2px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
