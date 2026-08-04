<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">设备托管</h2>
      <div class="page-actions">
        <span>{{ userStore.username }}</span>
        <el-button type="primary" link @click="logout">登出</el-button>
      </div>
    </div>

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
              <el-input v-model="manualForm.name" placeholder="留空自动生成" />
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
            <el-form-item label="发现方式">
              <el-radio-group v-model="discoverForm.discoverMode">
                <el-radio-button label="cidr">CIDR</el-radio-button>
                <el-radio-button label="range">IP 范围</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="discoverForm.discoverMode === 'cidr'" label="CIDR">
              <el-input v-model="discoverForm.cidr" placeholder="192.168.30.0/24" />
            </el-form-item>

            <template v-else>
              <el-form-item label="起始 IP">
                <el-input v-model="discoverForm.startIp" placeholder="192.168.30.1" />
              </el-form-item>
              <el-form-item label="结束 IP">
                <el-input v-model="discoverForm.endIp" placeholder="192.168.30.254" />
              </el-form-item>
            </template>

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
              <el-tag :type="statusType(runningTask.status)" size="small">{{ runningTask.status }}</el-tag>
            </div>
            <div class="progress-bar-row">
              <el-progress
                :percentage="progressPercent(runningTask)"
                :status="runningTask.status === 'RUNNING' ? '' : 'success'"
                :stroke-width="18"
                striped
                striped-flow
              />
              <span class="progress-count">{{ runningTask.scannedIpCount }} / {{ runningTask.totalIpCount }}</span>
            </div>
            <div class="progress-status">
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

      <el-table v-loading="loading" :data="tasks" size="small" stripe>
        <el-table-column prop="cidr" label="CIDR" width="150" show-overflow-tooltip />
        <el-table-column prop="portFrom" label="起始端口" width="85" />
        <el-table-column prop="portTo" label="结束端口" width="85" />
        <el-table-column label="进度" min-width="220">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="progressPercent(row)" :stroke-width="10" />
              <span class="progress-text">{{ row.scannedIpCount }} / {{ row.totalIpCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="foundCount" label="新增" width="70" />
        <el-table-column prop="message" label="结果" min-width="160" show-overflow-tooltip />
        <el-table-column prop="startedAt" label="开始时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="finishedAt" label="完成时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
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

    <!-- 发现任务详情抽屉 -->
    <el-drawer v-model="detailVisible" title="发现任务详情" size="600px">
      <el-descriptions v-if="currentTask" :column="2" border size="small">
        <el-descriptions-item label="CIDR">{{ currentTask.cidr }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(currentTask.status)" size="small">{{ currentTask.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="新增数量">{{ taskDetail.addedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="重复数量">{{ taskDetail.duplicateCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败数量">{{ taskDetail.failedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDateTime(currentTask.finishedAt) }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">逐 IP 结果</el-divider>
      <el-table :data="taskDetail.ipResults || []" size="small" stripe max-height="500">
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="result" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import { deviceApi, deviceGroupApi, discoverApi } from '@/api'
import { useUserStore } from '@/store'
import { authApi } from '@/api'
import { formatDateTime } from '@/utils/date'
import { isValidIPv4, ipRangeToCidr } from '@/utils/ip'

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

const groups = reactive([])
const tasks = reactive([])
const total = ref(0)
const loading = ref(false)
const manualSaving = ref(false)
const discoverSubmitting = ref(false)
const query = reactive({ page: 1, size: 20 })
const refreshTimer = ref(null)

const manualForm = reactive({ ip: '', port: 9082, mode: 'BRIDGE', groupId: null, name: '' })
const discoverForm = reactive({ discoverMode: 'cidr', cidr: '', startIp: '', endIp: '', portFrom: 9082, portTo: 9082 })

const runningTask = computed(() => {
  return tasks.find(t => t.status === 'RUNNING' || t.status === 'PENDING') || null
})

const detailVisible = ref(false)
const currentTask = ref(null)
const taskDetail = reactive({ addedCount: 0, duplicateCount: 0, failedCount: 0, ipResults: [] })

const statusType = (status) => {
  switch (status) {
    case 'DONE': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const resultTagType = (result) => {
  switch (result) {
    case 'ADDED': return 'success'
    case 'DUPLICATE': return 'warning'
    case 'ERROR': return 'danger'
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
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '添加失败')
  } finally {
    manualSaving.value = false
  }
}

function buildDiscoverPayload() {
  if (discoverForm.discoverMode === 'cidr') {
    return {
      cidr: discoverForm.cidr,
      portFrom: discoverForm.portFrom,
      portTo: discoverForm.portTo
    }
  }
  if (!isValidIPv4(discoverForm.startIp) || !isValidIPv4(discoverForm.endIp)) {
    throw new Error('起始 IP 或结束 IP 格式不正确')
  }
  const cidrs = ipRangeToCidr(discoverForm.startIp, discoverForm.endIp)
  if (!cidrs || cidrs.length === 0) {
    throw new Error('IP 范围无效')
  }
  if (cidrs.length > 1) {
    throw new Error('IP 范围跨多个 CIDR，请拆分或使用 CIDR 输入')
  }
  return {
    cidr: cidrs[0],
    portFrom: discoverForm.portFrom,
    portTo: discoverForm.portTo
  }
}

const submitDiscover = async () => {
  discoverSubmitting.value = true
  try {
    const payload = buildDiscoverPayload()
    await discoverApi.scan(payload)
    ElMessage.success('扫描任务已提交')
    loadTasks()
    startRefresh()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
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

const openDetail = async (row) => {
  currentTask.value = row
  try {
    const res = await discoverApi.taskDetail(row.id)
    const task = res.data
    Object.assign(taskDetail, { addedCount: 0, duplicateCount: 0, failedCount: 0, ipResults: [] })
    if (task.detail) {
      try {
        const d = JSON.parse(task.detail)
        Object.assign(taskDetail, d)
      } catch (e) {
        console.error('解析详情失败', e)
      }
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
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
.progress-bar-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.progress-bar-row :deep(.el-progress) {
  flex: 1;
}
.progress-bar-row .progress-count {
  flex-shrink: 0;
}
.progress-cell {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.progress-cell :deep(.el-progress) {
  flex: 1;
  min-width: 0;
}
.progress-text {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1;
  white-space: nowrap;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.table-progress-text {
  font-size: 12px;
  color: var(--text-muted);
  text-align: right;
  margin-top: 2px;
}
</style>
