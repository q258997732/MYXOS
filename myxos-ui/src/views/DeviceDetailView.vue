<template>
  <div class="page-container">
    <h2 class="page-title">设备详情</h2>

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
          <el-descriptions-item label="最后在线">{{ device.lastSeenAt || '-' }}</el-descriptions-item>
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
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="name in androids" :key="name">
            <el-card class="android-card" shadow="hover">
              <template #header>
                <div class="android-header">
                  <el-icon><Cellphone /></el-icon>
                  <span class="android-name" :title="name">{{ name }}</span>
                </div>
              </template>
              <el-button size="small" type="primary" @click="selectInstance(name)">选择并操作</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="实时指标" name="metrics">
        <el-table v-loading="metricsLoading" :data="metrics" size="small" stripe>
          <el-table-column prop="metricType" label="指标类型" width="140" />
          <el-table-column prop="metricValue" label="指标值" />
          <el-table-column prop="collectedAt" label="采集时间" width="180" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="手动操作" name="ops">
        <div class="op-section">
          <h4>主机级</h4>
          <el-button-group>
            <el-button @click="submitOp('REBOOT_HOST')">重启主机</el-button>
          </el-button-group>
        </div>

        <div class="op-section">
          <h4>容器生命周期</h4>
          <el-form inline>
            <el-form-item label="容器名称">
              <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
                <el-option v-for="name in androids" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
            <el-form-item label="新名称" v-if="showRename">
              <el-input v-model="newInstanceName" placeholder="重命名时填写" style="width: 200px;" />
            </el-form-item>
          </el-form>
          <el-button-group>
            <el-button @click="submitAndroidOp('RUN_ANDROID')">启动</el-button>
            <el-button @click="submitAndroidOp('STOP_ANDROID')">停止</el-button>
            <el-button @click="submitAndroidOp('REBOOT_ANDROID')">重启</el-button>
            <el-button @click="submitAndroidOp('RESET_ANDROID')">重置</el-button>
            <el-button v-if="!showRename" @click="showRename = true">重命名</el-button>
            <el-button v-else type="primary" @click="submitAndroidOp('RENAME_ANDROID')">确认重命名</el-button>
          </el-button-group>
        </div>

        <div class="op-section">
          <h4>安卓实例操作</h4>
          <el-form inline>
            <el-form-item label="容器名称">
              <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
                <el-option v-for="name in androids" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-button-group>
            <el-button @click="submitScreenshot">截图（临时查看）</el-button>
            <el-button @click="openDialog('clipboard')">设置剪贴板</el-button>
            <el-button @click="submitAndroidOp('GET_CLIPBOARD')">获取剪贴板</el-button>
            <el-button @click="openDialog('language')">设置语言</el-button>
            <el-button @click="submitAndroidOp('REFRESH_LOCATION')">IP 智能定位</el-button>
            <el-button @click="openDialog('shell')">执行 Adb 命令</el-button>
          </el-button-group>
        </div>

        <!-- 截图临时预览 -->
        <el-dialog v-model="screenshotVisible" title="设备截图" width="400px">
          <img v-if="screenshotData" :src="screenshotData" style="max-width: 100%;" />
          <span v-else>暂无截图数据</span>
        </el-dialog>

        <!-- 参数对话框 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="400px">
          <el-form v-if="dialogType === 'clipboard'">
            <el-form-item label="文本内容">
              <el-input v-model="dialogForm.text" type="textarea" />
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
          <el-form v-if="dialogType === 'shell'">
            <el-form-item label="Adb 命令">
              <el-input v-model="dialogForm.command" type="textarea" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmDialog">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="最近告警" name="alarms">
        <el-table :data="alarms" size="small" stripe>
          <el-table-column prop="ruleName" label="规则" />
          <el-table-column prop="metricValue" label="指标值" />
          <el-table-column prop="firedAt" label="触发时间" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="最近日志" name="logs">
        <el-table :data="logs" size="small" stripe>
          <el-table-column prop="logLevel" label="级别" />
          <el-table-column prop="message" label="消息" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="任务记录" name="tasks">
        <el-table :data="tasks" size="small" stripe>
          <el-table-column prop="operationCode" label="操作" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="resultMsg" label="结果" />
          <el-table-column prop="finishedAt" label="完成时间" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cellphone } from '@element-plus/icons-vue'
import { deviceApi } from '@/api'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'

const route = useRoute()
const deviceId = route.params.id
const activeTab = ref('info')
const device = reactive({})
const androids = reactive([])
const metrics = reactive([])
const alarms = reactive([])
const logs = reactive([])
const tasks = reactive([])

const androidLoading = ref(false)
const metricsLoading = ref(false)

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
    const res = await deviceApi.metrics(deviceId, { page: 1, size: 50 })
    metrics.splice(0, metrics.length, ...(res.data.records || []))
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
  if (type === 'clipboard') {
    dialogTitle.value = '设置剪贴板'
  } else if (type === 'language') {
    dialogTitle.value = '设置系统语言'
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
  } else if (dialogType.value === 'language') {
    params.country = dialogForm.country
    params.language = dialogForm.language
    await submitOp('SET_LANGUAGE', params)
  } else if (dialogType.value === 'shell') {
    params.command = dialogForm.command
    await submitOp('SHELL_ADB', params)
  }
  dialogVisible.value = false
}

onMounted(() => {
  loadDevice()
  loadAndroids()
  loadMetrics()
  loadAlarms()
  loadLogs()
  loadTasks()
})
</script>

<style scoped>
.op-section {
  margin-bottom: 20px;
}
.op-section h4 {
  margin: 10px 0;
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
</style>
