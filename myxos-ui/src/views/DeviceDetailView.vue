<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">设备详情</h2>
      <div class="page-actions">
        <span>{{ userStore.username }}</span>
        <el-button type="primary" link @click="logout">登出</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="名称">{{ device.name }}</el-descriptions-item>
          <el-descriptions-item label="IP:Port">{{ device.ip }}:{{ device.port }}</el-descriptions-item>
          <el-descriptions-item label="模式">{{ device.mode }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <DeviceStatusTag :status="device.status" />
          </el-descriptions-item>
          <el-descriptions-item label="版本">{{ device.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后在线">{{ formatDateTime(device.lastSeenAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="安卓实例" name="androids">
        <el-alert
          v-if="androids.length === 0 && !androidLoading"
          title="暂无安卓实例"
          description="未从该设备获取到安卓实例，请确认设备在线后刷新。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-row :gutter="16" v-loading="androidLoading">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in androids" :key="item.name">
            <el-card class="android-card" shadow="hover">
              <template #header>
                <div class="android-header">
                  <el-icon><Cellphone /></el-icon>
                  <span class="android-name" :title="item.name">{{ item.name }}</span>
                  <el-tag :type="androidStatusType(item.status)" size="small">{{ item.statusLabel }}</el-tag>
                </div>
              </template>
              <el-button size="small" type="primary" @click="selectInstance(item.name)">选择并操作</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="实时指标" name="metrics">
        <div v-loading="metricsLoading">
          <h4 class="metric-section-title">主机指标</h4>
          <el-empty v-if="hostMetrics.length === 0" description="暂无主机指标" />
          <el-row :gutter="16" v-else>
            <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in hostMetrics" :key="item.metricType">
              <el-card class="metric-card" shadow="hover" @click="openMetricHistory(item)">
                <div class="metric-title">{{ metricLabel(item.metricType) }}</div>
                <div class="metric-value">{{ formatMetricValue(item) }}</div>
                <div class="metric-time">{{ formatDateTime(item.collectedAt) }}</div>
              </el-card>
            </el-col>
          </el-row>

          <h4 class="metric-section-title">安卓实例状态</h4>
          <el-empty v-if="androidMetrics.length === 0" description="暂无安卓实例状态" />
          <el-row :gutter="16" v-else>
            <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in androidMetrics" :key="item.metricType + item.extra">
              <el-card class="metric-card" shadow="hover" @click="openMetricHistory(item)">
                <div class="metric-title">{{ androidMetricTitle(item) }}</div>
                <div class="metric-value">{{ formatMetricValue(item) }}</div>
                <div class="metric-time">{{ formatDateTime(item.collectedAt) }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <el-tab-pane label="手动操作" name="ops">
        <!-- 主机级 -->
        <el-card class="op-card" shadow="never">
          <template #header><div class="op-card-header">主机操作</div></template>
          <el-alert type="info" :closable="false" show-icon title="主机级操作会影响整台设备">
            以下操作针对当前设备（{{ device.ip }}:{{ device.port }}）本身执行。
          </el-alert>
          <el-button-group class="op-buttons">
            <el-button type="danger" plain @click="submitOp('REBOOT_HOST')">重启主机</el-button>
          </el-button-group>
        </el-card>

        <!-- 安卓容器级 -->
        <el-card class="op-card" shadow="never">
          <template #header><div class="op-card-header">安卓容器操作</div></template>
          <el-alert type="info" :closable="false" show-icon title="容器级操作会改变容器的运行状态">
            请选择下方容器，然后执行启动、停止、重启、重置或重命名。
          </el-alert>
          <el-form inline>
            <el-form-item label="容器名称">
              <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
                <el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="新名称" v-if="showRename">
              <el-input v-model="newInstanceName" placeholder="重命名时填写" style="width: 200px;" />
            </el-form-item>
          </el-form>
          <el-button-group class="op-buttons">
            <el-button @click="submitAndroidOp('RUN_ANDROID')">启动容器</el-button>
            <el-button @click="submitAndroidOp('STOP_ANDROID')">停止容器</el-button>
            <el-button @click="submitAndroidOp('REBOOT_ANDROID')">重启容器</el-button>
            <el-button type="warning" plain @click="submitAndroidOp('RESET_ANDROID')">重置容器</el-button>
            <el-button v-if="!showRename" @click="showRename = true">重命名</el-button>
            <el-button v-else type="primary" @click="submitAndroidOp('RENAME_ANDROID')">确认重命名</el-button>
          </el-button-group>
        </el-card>

        <!-- 安卓实例级 -->
        <el-card class="op-card" shadow="never">
          <template #header><div class="op-card-header">安卓实例操作</div></template>
          <el-alert type="info" :closable="false" show-icon title="实例级操作针对运行中的安卓系统">
            请选择容器后执行截图、剪贴板、语言设置、IP 定位或 Adb 命令。
          </el-alert>
          <el-form inline>
            <el-form-item label="容器名称">
              <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
                <el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-button-group class="op-buttons">
            <el-button @click="submitScreenshot">截图（临时查看）</el-button>
            <el-button @click="openDialog('clipboard')">设置剪贴板</el-button>
            <el-button @click="submitClipboardGet">获取剪贴板</el-button>
            <el-button @click="openDialog('language')">设置语言</el-button>
            <el-button @click="openDialog('location')">IP 智能定位</el-button>
            <el-button @click="openDialog('shell')">执行 Adb 命令</el-button>
          </el-button-group>
        </el-card>

        <!-- 截图临时预览 -->
        <el-dialog v-model="screenshotVisible" title="设备截图" width="fit-content" align-center destroy-on-close>
          <img v-if="screenshotData" :src="screenshotData" style="max-width: 80vw; max-height: 80vh; display: block;" />
          <span v-else>暂无截图数据</span>
        </el-dialog>

        <!-- 参数/结果对话框 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" align-center destroy-on-close>
          <el-form v-if="dialogType === 'clipboard'">
            <el-form-item label="文本内容">
              <el-input v-model="dialogForm.text" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
          <el-form v-if="dialogType === 'language'">
            <el-form-item label="国家">
              <el-input v-model="dialogForm.country" placeholder="如 cn" />
            </el-form-item>
            <el-form-item label="语言">
              <el-input v-model="dialogForm.language" placeholder="如 zh" />
            </el-form-item>
          </el-form>
          <el-form v-if="dialogType === 'location'">
            <el-form-item label="语言">
              <el-input v-model="dialogForm.language" placeholder="如 zh" />
            </el-form-item>
          </el-form>
          <el-form v-if="dialogType === 'shell'">
            <el-form-item label="Adb 命令">
              <el-input v-model="dialogForm.command" type="textarea" :rows="3" placeholder="例如：pm list packages" />
            </el-form-item>
            <el-form-item v-if="dialogResult" label="执行结果">
              <pre class="shell-result">{{ dialogResult }}</pre>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmDialog" :loading="dialogLoading">确定</el-button>
          </template>
        </el-dialog>

        <!-- 剪贴板内容展示 -->
        <el-dialog v-model="clipboardVisible" title="剪贴板内容" width="480px" align-center destroy-on-close>
          <el-input v-model="clipboardData" type="textarea" :rows="6" readonly />
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="最近告警" name="alarms">
        <el-table :data="alarms" size="small" stripe>
          <el-table-column prop="ruleName" label="规则" />
          <el-table-column prop="metricValue" label="指标值" />
          <el-table-column label="触发时间">
            <template #default="{ row }">{{ formatDateTime(row.firedAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="最近日志" name="logs">
        <el-table :data="logs" size="small" stripe>
          <el-table-column prop="logLevel" label="级别" />
          <el-table-column prop="message" label="消息" />
          <el-table-column label="时间">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="任务记录" name="tasks">
        <el-table :data="tasks" size="small" stripe>
          <el-table-column prop="operationCode" label="操作" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="resultMsg" label="结果" />
          <el-table-column label="完成时间">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 指标历史抽屉 -->
    <el-drawer v-model="historyVisible" :title="historyTitle" size="600px">
      <el-table v-loading="historyLoading" :data="historyRecords" size="small" stripe>
        <el-table-column prop="metricValue" label="指标值" />
        <el-table-column label="采集时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="historyQuery.page"
          v-model:page-size="historyQuery.size"
          :total="historyTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadMetricHistory"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cellphone } from '@element-plus/icons-vue'
import { deviceApi, authApi } from '@/api'
import { useUserStore } from '@/store'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'
import { formatDateTime } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const deviceId = route.params.id
const activeTab = ref('info')
const device = reactive({})
const androids = reactive([])
const latestMetrics = reactive([])
const alarms = reactive([])
const logs = reactive([])
const tasks = reactive([])

const androidLoading = ref(false)
const metricsLoading = ref(false)
const historyLoading = ref(false)

const instanceName = ref('')
const newInstanceName = ref('')
const showRename = ref(false)

const screenshotVisible = ref(false)
const screenshotData = ref('')

const dialogVisible = ref(false)
const dialogType = ref('')
const dialogTitle = ref('')
const dialogForm = reactive({
  text: '',
  country: '',
  language: '',
  command: ''
})

const dialogLoading = ref(false)
const dialogResult = ref('')
const clipboardVisible = ref(false)
const clipboardData = ref('')

const historyVisible = ref(false)
const historyTitle = ref('')
const historyRecords = reactive([])
const historyTotal = ref(0)
const historyQuery = reactive({ page: 1, size: 20, metricType: '' })

const METRIC_LABELS = {
  CPU: 'CPU 使用率',
  MEM: '内存使用率',
  DISK: '磁盘使用率',
  NET_RX: '网络接收',
  NET_TX: '网络发送',
  TEMP: '温度',
  UPTIME: '运行时长',
  VERSION: '版本号',
  ANDROID_STATUS: '安卓实例状态'
}

let refreshTimer = null

async function logout() {
  try {
    await authApi.logout()
  } catch (e) {
    // ignore
  }
  userStore.clearUser()
  router.push('/login')
}

async function loadDevice() {
  const res = await deviceApi.detail(deviceId)
  Object.assign(device, res.data)
}

async function loadAndroids() {
  androidLoading.value = true
  try {
    const res = await deviceApi.androids(deviceId)
    androids.splice(0, androids.length, ...(res.data || []))
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    androidLoading.value = false
  }
}

async function loadMetrics() {
  metricsLoading.value = true
  try {
    const res = await deviceApi.latestMetrics(deviceId)
    latestMetrics.splice(0, latestMetrics.length, ...(res.data || []))
  } finally {
    metricsLoading.value = false
  }
}

async function loadAlarms() {
  const res = await deviceApi.alarms(deviceId, { page: 1, size: 20 })
  alarms.splice(0, alarms.length, ...res.data.records)
}

async function loadLogs() {
  const res = await deviceApi.logs(deviceId, { page: 1, size: 50 })
  logs.splice(0, logs.length, ...res.data.records)
}

async function loadTasks() {
  const res = await deviceApi.tasks(deviceId, { page: 1, size: 20 })
  tasks.splice(0, tasks.length, ...res.data.records)
}

const hostMetrics = computed(() => latestMetrics.filter(m => m.metricType !== 'ANDROID_STATUS'))
const androidMetrics = computed(() => latestMetrics.filter(m => m.metricType === 'ANDROID_STATUS'))

function androidMetricTitle(item) {
  try {
    const extra = JSON.parse(item.extra)
    return (extra.name || '未知容器') + ' 状态'
  } catch (e) {
    return '安卓实例状态'
  }
}

function metricLabel(type) {
  return METRIC_LABELS[type] || type
}

function formatMetricValue(item) {
  if (item.metricType === 'ANDROID_STATUS' && item.extra) {
    try {
      const extra = JSON.parse(item.extra)
      return `${extra.name || '-'}: ${item.metricValue}`
    } catch (e) {
      return item.metricValue
    }
  }
  return item.metricValue || '-'
}

function androidStatusType(status) {
  if (status === 'RUNNING') return 'success'
  if (status === 'STOPPED') return 'danger'
  return 'info'
}

function selectInstance(name) {
  instanceName.value = name
  activeTab.value = 'ops'
  ElMessage.success(`已选择容器：${name}`)
}

async function submitOp(code, params = {}) {
  await deviceApi.ops(deviceId, { operationCode: code, params })
  ElMessage.success('任务已提交')
}

async function submitAndroidOp(code) {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  const params = { name: instanceName.value }
  if (code === 'RENAME_ANDROID') {
    if (!newInstanceName.value) {
      ElMessage.warning('请输入新容器名称')
      return
    }
    params.newName = newInstanceName.value
    showRename.value = false
  }
  await submitOp(code, params)
}

async function submitScreenshot() {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  try {
    const res = await deviceApi.screenshot(deviceId, {
      name: instanceName.value,
      level: '1'
    })
    const d = res.data
    screenshotData.value = normalizeImageData(d)
    screenshotVisible.value = true
  } catch (e) {
    ElMessage.error('截图失败：' + (e.message || '未知错误'))
  }
}

function normalizeImageData(d) {
  if (!d) {
    return ''
  }
  if (d.startsWith('data:image/jpeg') || d.startsWith('data:image/png')) {
    return d
  }
  if (d.startsWith('http://') || d.startsWith('https://')) {
    return d
  }
  if (d.startsWith('/9j/')) {
    return `data:image/jpeg;base64,${d}`
  }
  if (d.startsWith('iVBORw0KGgo')) {
    return `data:image/png;base64,${d}`
  }
  return ''
}

function openDialog(type) {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  dialogType.value = type
  dialogResult.value = ''
  if (type === 'clipboard') {
    dialogTitle.value = '设置剪贴板'
  } else if (type === 'language') {
    dialogTitle.value = '设置系统语言'
  } else if (type === 'location') {
    dialogTitle.value = 'IP 智能定位'
  } else if (type === 'shell') {
    dialogTitle.value = '执行 Adb 命令'
  }
  dialogVisible.value = true
}

async function confirmDialog() {
  const params = { name: instanceName.value }
  if (dialogType.value === 'clipboard') {
    params.text = dialogForm.text
    await submitOp('SET_CLIPBOARD', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'language') {
    params.country = dialogForm.country
    params.language = dialogForm.language
    await submitOp('SET_LANGUAGE', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'location') {
    if (!dialogForm.language) {
      ElMessage.warning('请输入语言参数')
      return
    }
    params.language = dialogForm.language
    await submitOp('REFRESH_LOCATION', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'shell') {
    if (!dialogForm.command) {
      ElMessage.warning('请输入 Adb 命令')
      return
    }
    dialogLoading.value = true
    try {
      const res = await deviceApi.shell(deviceId, {
        name: instanceName.value,
        command: dialogForm.command
      })
      dialogResult.value = res.data || '执行成功，无返回'
      ElMessage.success('命令执行成功')
    } catch (e) {
      dialogResult.value = '执行失败：' + (e.message || '未知错误')
      ElMessage.error('命令执行失败')
    } finally {
      dialogLoading.value = false
    }
  }
}

async function submitClipboardGet() {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  try {
    const res = await deviceApi.clipboardGet(deviceId, { name: instanceName.value })
    clipboardData.value = res.data || '（空）'
    clipboardVisible.value = true
  } catch (e) {
    ElMessage.error('获取剪贴板失败：' + (e.message || '未知错误'))
  }
}

function openMetricHistory(item) {
  historyQuery.metricType = item.metricType
  historyTitle.value = `${metricLabel(item.metricType)} - 采集记录`
  historyQuery.page = 1
  historyVisible.value = true
  loadMetricHistory()
}

async function loadMetricHistory() {
  historyLoading.value = true
  try {
    const res = await deviceApi.metricHistory(deviceId, {
      metricType: historyQuery.metricType,
      page: historyQuery.page,
      size: historyQuery.size
    })
    historyRecords.splice(0, historyRecords.length, ...(res.data.records || []))
    historyTotal.value = res.data.total || 0
  } finally {
    historyLoading.value = false
  }
}

function startRefresh() {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    if (activeTab.value === 'metrics') {
      loadMetrics()
    }
  }, 5000)
}

function stopRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  loadDevice()
  loadAndroids()
  loadMetrics()
  loadAlarms()
  loadLogs()
  loadTasks()
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
})
</script>

<style scoped>
.op-card {
  margin-bottom: var(--spacing-md);
}
.op-card-header {
  font-weight: 600;
}
.op-buttons {
  margin-top: var(--spacing-sm);
}
.shell-result {
  background-color: #f5f7fa;
  padding: var(--spacing-sm);
  border-radius: var(--border-radius);
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.android-card {
  margin-bottom: var(--spacing-md);
  border-radius: var(--border-radius);
}
.android-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.android-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.metric-card {
  margin-bottom: var(--spacing-md);
  border-radius: var(--border-radius);
  cursor: pointer;
  transition: transform 0.2s;
}
.metric-card:hover {
  transform: translateY(-2px);
}
.metric-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.metric-time {
  font-size: 12px;
  color: var(--text-muted);
}
.pagination-bar {
  margin-top: var(--spacing-md);
  display: flex;
  justify-content: flex-end;
}
</style>
