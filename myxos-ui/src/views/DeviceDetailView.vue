<template>
  <div>
    <h2>设备详情</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="名称">{{ device.name }}</el-descriptions-item>
          <el-descriptions-item label="IP:Port">{{ device.ip }}:{{ device.port }}</el-descriptions-item>
          <el-descriptions-item label="模式">{{ device.mode }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <DeviceStatusTag :status="device.status" />
          </el-descriptions-item>
          <el-descriptions-item label="版本">{{ device.version }}</el-descriptions-item>
          <el-descriptions-item label="最后在线">{{ device.lastSeenAt }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="实时指标" name="metrics">
        <div ref="chartRef" style="height: 300px;"></div>
      </el-tab-pane>

      <el-tab-pane label="手动操作" name="ops">
        <el-button-group>
          <el-button @click="submitOp('REBOOT')">重启</el-button>
          <el-button @click="submitOp('ADB_ON')">开启 ADB</el-button>
          <el-button @click="submitOp('ADB_OFF')">关闭 ADB</el-button>
          <el-button @click="submitOp('KEEPALIVE_ON')">开启保活</el-button>
          <el-button @click="submitOp('KEEPALIVE_OFF')">关闭保活</el-button>
        </el-button-group>
      </el-tab-pane>

      <el-tab-pane label="最近告警" name="alarms">
        <el-table :data="alarms" size="small">
          <el-table-column prop="ruleName" label="规则" />
          <el-table-column prop="metricValue" label="指标值" />
          <el-table-column prop="firedAt" label="触发时间" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="最近日志" name="logs">
        <el-table :data="logs" size="small">
          <el-table-column prop="logLevel" label="级别" />
          <el-table-column prop="message" label="消息" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="任务记录" name="tasks">
        <el-table :data="tasks" size="small">
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
import { deviceApi } from '@/api'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'

const route = useRoute()
const deviceId = route.params.id
const activeTab = ref('info')
const device = reactive({})
const alarms = reactive([])
const logs = reactive([])
const tasks = reactive([])
const chartRef = ref(null)

async function loadDevice() {
  const res = await deviceApi.detail(deviceId)
  Object.assign(device, res.data)
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

async function submitOp(code) {
  await deviceApi.ops(deviceId, { operationCode: code })
  ElMessage.success('任务已提交')
}

onMounted(() => {
  loadDevice()
  loadAlarms()
  loadLogs()
  loadTasks()
})
</script>
