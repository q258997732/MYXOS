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
              <div class="android-meta">
                <div v-if="item.image" class="android-meta-item">
                  <el-icon><Picture /></el-icon>
                  <span>镜像: {{ item.image }}</span>
                </div>
                <div v-if="item.ip" class="android-meta-item">
                  <el-icon><MapLocation /></el-icon>
                  <span>IP: {{ item.ip }}</span>
                </div>
                <div v-if="item.statusDetail" class="android-meta-item">
                  <el-icon><InfoFilled /></el-icon>
                  <span :title="item.statusDetail">{{ item.statusDetail }}</span>
                </div>
              </div>
              <div class="android-actions">
                <el-button size="small" type="primary" plain @click="quickScreenshot(item.name)">截图</el-button>
                <el-button size="small" type="success" @click="quickOp('RUN_ANDROID', item.name)">启动</el-button>
                <el-button size="small" type="danger" @click="quickOp('STOP_ANDROID', item.name)">停止</el-button>
                <el-button size="small" type="warning" @click="quickOp('REBOOT_ANDROID', item.name)">重启</el-button>
              </div>
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
          <el-empty v-if="androids.length === 0" description="暂无安卓实例状态" />
          <el-row :gutter="16" v-else>
            <el-col :xs="24" :sm="24" :md="12" :lg="12" v-for="item in androids" :key="item.name">
              <el-card class="metric-card android-status-card" shadow="hover" @click="selectInstance(item.name)">
                <div class="android-status-header">
                  <el-tag :type="androidStatusType(item.status)" size="small">{{ item.statusLabel }}</el-tag>
                  <span class="android-status-name" :title="item.name">{{ item.name }}</span>
                </div>
                <div class="android-status-detail" v-if="item.statusDetail">{{ item.statusDetail }}</div>
                <div class="metric-time" v-if="item.image">镜像: {{ item.image }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <el-tab-pane label="手动操作" name="ops">
        <!-- 主机操作 -->
        <el-card class="op-card" shadow="never">
          <template #header>
            <div class="op-card-header">主机操作</div>
          </template>
          <el-alert type="info" :closable="false" show-icon title="主机级操作会影响整台设备及其安卓实例">
            主机级操作无需选择实例，直接执行即可。
          </el-alert>
          <div class="op-buttons">
            <el-button type="danger" @click="submitOp('REBOOT_HOST')">重启主机</el-button>
          </div>
        </el-card>

        <!-- 实例操作 -->
        <el-card class="op-card" shadow="never">
          <template #header>
            <div class="op-card-header">实例操作</div>
          </template>
          <el-alert type="info" :closable="false" show-icon title="实例级操作针对选中的安卓系统">
            请先选择目标实例，再执行对应操作。
          </el-alert>
          <el-form inline class="op-form">
            <el-form-item label="实例名称">
              <el-select v-model="instanceName" placeholder="请选择实例" filterable style="width: 260px">
                <el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="新名称" v-if="showRename">
              <el-input v-model="newInstanceName" placeholder="重命名时填写" style="width: 220px;" />
            </el-form-item>
          </el-form>

          <div class="op-group">
            <div class="op-group-title">生命周期</div>
            <div class="op-buttons">
              <el-button type="success" @click="submitAndroidOp('RUN_ANDROID')">启动</el-button>
              <el-button type="info" @click="submitAndroidOp('STOP_ANDROID')">停止</el-button>
              <el-button type="warning" @click="submitAndroidOp('REBOOT_ANDROID')">重启</el-button>
              <el-button @click="submitAndroidOp('RESET_ANDROID')">重置</el-button>
              <el-button v-if="!showRename" @click="showRename = true">重命名</el-button>
              <el-button v-else type="primary" @click="submitAndroidOp('RENAME_ANDROID')">确认重命名</el-button>
            </div>
          </div>

          <div class="op-group">
            <div class="op-group-title">剪贴板</div>
            <div class="op-buttons">
              <el-button @click="openDialog('clipboard')">设置剪贴板</el-button>
              <el-button @click="submitClipboardGet">获取剪贴板</el-button>
            </div>
          </div>

          <div class="op-group">
            <div class="op-group-title">其他</div>
            <div class="op-buttons">
              <el-button type="primary" plain @click="submitScreenshot">截图（临时查看）</el-button>
              <el-button @click="openDialog('language')">设置语言</el-button>
              <el-button @click="openDialog('location')">IP 智能定位</el-button>
              <el-button @click="openDialog('shell')">执行 Adb 命令</el-button>
            </div>
          </div>
        </el-card>
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
          <el-table-column prop="operationCode" label="操作" width="140" show-overflow-tooltip />
          <el-table-column label="参数" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTaskParams(row.params) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="taskStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resultMsg" label="结果" min-width="200" show-overflow-tooltip />
          <el-table-column label="完成时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

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
          <el-input v-model="dialogForm.command" type="textarea" :rows="3"
            placeholder="输入容器内 shell 命令，例如：pm list packages、getprop ro.build.version.release、dumpsys battery" />
          <div class="shell-tip">命令在容器内 shell 直接执行，无需 adb 前缀；常用示例：pm list packages（列出应用）、input keyevent 3（返回桌面）、settings list system（系统设置）</div>
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

    <!-- 指标历史抽屉 -->
    <el-drawer v-model="historyVisible" :title="historyTitle" size="700px">
      <div ref="historyChartRef" class="history-chart" v-show="historyChartVisible"></div>
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
import { reactive, ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cellphone, Picture, MapLocation, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
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
const historyChartRef = ref(null)
const historyChartVisible = ref(false)
let historyChart = null
let taskPollTimer = null

const TASK_POLL_MAX_COUNT = 10
const TASK_POLL_INTERVAL_MS = 3000

const METRIC_LABELS = {
  CPU: 'CPU 使用率',
  MEM: '内存使用率',
  DISK: '磁盘使用率',
  NET_RX: '网络接收',
  NET_TX: '网络发送',
  TEMP: '温度',
  UPTIME: '运行时长',
  VERSION: '版本号',
  ANDROID_STATUS: '安卓实例状态',
  ONLINE: '设备在线',
  OFFLINE: '设备离线',
  ANDROID_ONLINE: '安卓实例在线数',
  ANDROID_OFFLINE: '安卓实例离线数'
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

function metricLabel(type) {
  return METRIC_LABELS[type] || type
}

function formatMetricValue(item) {
  return item.metricValue || '-'
}

function androidStatusType(status) {
  if (status === 'RUNNING') return 'success'
  if (status === 'STOPPED') return 'danger'
  if (status === 'TRANSITION') return 'warning'
  return 'info'
}

function taskStatusType(status) {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    default: return 'info'
  }
}

function formatTaskParams(params) {
  if (!params) return '-'
  try {
    const p = JSON.parse(params)
    const parts = []
    if (p.name) parts.push(`实例: ${p.name}`)
    if (p.text) parts.push(`文本: ${p.text}`)
    if (p.command) parts.push(`命令: ${p.command}`)
    if (p.country) parts.push(`国家: ${p.country}`)
    if (p.language) parts.push(`语言: ${p.language}`)
    if (p.newName) parts.push(`新名称: ${p.newName}`)
    return parts.join(' | ') || params
  } catch (e) {
    return params
  }
}

function selectInstance(name) {
  instanceName.value = name
  activeTab.value = 'ops'
  ElMessage.success(`已选择实例：${name}`)
}

async function quickOp(code, name) {
  instanceName.value = name
  await submitOp(code, { name })
}

async function quickScreenshot(name) {
  instanceName.value = name
  await submitScreenshot()
}

async function submitOp(code, params = {}) {
  await deviceApi.ops(deviceId, { operationCode: code, params })
  ElMessage.success('任务已提交')
  try {
    await loadTasks()
  } catch (e) {
    // 错误已在拦截器中提示，避免阻塞弹窗关闭
  }
  if (activeTab.value === 'tasks') {
    startTaskPolling()
  }
}

function startTaskPolling() {
  stopTaskPolling()
  let count = 0
  taskPollTimer = setInterval(async () => {
    try {
      await loadTasks()
    } catch (e) {
      // 错误已在拦截器中提示，继续轮询直到达到上限
    } finally {
      count++
      if (count >= TASK_POLL_MAX_COUNT) {
        stopTaskPolling()
      }
    }
  }, TASK_POLL_INTERVAL_MS)
}

function stopTaskPolling() {
  if (taskPollTimer) {
    clearInterval(taskPollTimer)
    taskPollTimer = null
  }
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
  dialogForm.text = ''
  dialogForm.country = ''
  dialogForm.language = ''
  dialogForm.command = ''
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
    renderHistoryChart()
  } finally {
    historyLoading.value = false
  }
}

async function renderHistoryChart() {
  if (!historyChartRef.value) return
  const records = [...historyRecords].reverse()
  const numericValues = records.map(r => parseFloat(r.metricValue)).filter(v => !isNaN(v))
  if (numericValues.length === 0) {
    historyChartVisible.value = false
    return
  }
  historyChartVisible.value = true
  await nextTick()
  if (!historyChart) {
    historyChart = echarts.init(historyChartRef.value)
    window.addEventListener('resize', handleChartResize)
  }
  const xData = records.map(r => formatDateTime(r.collectedAt))
  const yData = records.map(r => {
    const v = parseFloat(r.metricValue)
    return isNaN(v) ? null : v
  })
  historyChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: xData, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [{
      name: historyTitle.value,
      type: 'line',
      smooth: true,
      data: yData,
      areaStyle: { opacity: 0.2 },
      connectNulls: false
    }]
  }, true)
}

function handleChartResize() {
  if (historyChart) {
    historyChart.resize()
  }
}

function startRefresh() {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    if (activeTab.value === 'metrics') {
      loadMetrics()
      loadAndroids()
    }
  }, 5000)
}

function stopRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

watch(activeTab, (val) => {
  if (val === 'tasks') {
    loadTasks()
    startTaskPolling()
  } else {
    stopTaskPolling()
  }
})

watch(historyVisible, (val) => {
  if (!val) {
    historyChartVisible.value = false
    if (historyChart) {
      window.removeEventListener('resize', handleChartResize)
      historyChart.dispose()
      historyChart = null
    }
  }
})

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
  stopTaskPolling()
  if (historyChart) {
    window.removeEventListener('resize', handleChartResize)
    historyChart.dispose()
    historyChart = null
  }
})
</script>

<style scoped>
.op-card {
  margin-bottom: var(--spacing-lg);
}
.op-card-header {
  font-weight: 600;
}
.op-form {
  margin-top: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}
.op-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.op-buttons .el-button {
  margin-left: 0 !important;
}
.op-group {
  margin-bottom: var(--spacing-md);
}
.op-group-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
  font-weight: 600;
}
.op-group .op-buttons {
  margin-top: 0;
}
.history-chart {
  width: 100%;
  height: 260px;
  margin-bottom: var(--spacing-md);
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
.shell-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
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
.android-meta {
  margin-bottom: var(--spacing-md);
  font-size: 13px;
  color: var(--text-secondary);
}
.android-meta-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-xs);
  line-height: 1.5;
}
.android-meta-item span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.android-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
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
.android-status-card {
  padding-top: var(--spacing-sm);
}
.android-status-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}
.android-status-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 14px;
}
.android-status-detail {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
