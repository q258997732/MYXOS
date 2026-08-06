<template>
  <div class="page-container">
    <div class="page-header"><h2 class="page-title">设备详情</h2><el-button @click="$router.back()">返回</el-button></div>
    <el-card v-loading="loading" class="content-card">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="名称">{{ device.name || '-' }}</el-descriptions-item><el-descriptions-item label="IP:Port">{{ device.ip }}:{{ device.port }}</el-descriptions-item>
        <el-descriptions-item label="状态"><DeviceStatusTag :status="device.status" /></el-descriptions-item><el-descriptions-item label="模式">{{ device.mode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ device.version || '-' }}</el-descriptions-item><el-descriptions-item label="最后在线">{{ formatDateTime(device.lastSeenAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-tabs v-model="activeTab" class="content-card">
      <el-tab-pane label="实时指标" name="metrics">
        <h4>主机指标</h4>
        <el-table :data="hostMetrics" size="small" stripe><el-table-column prop="metricCode" label="指标" /><el-table-column prop="metricValue" label="当前值" /><el-table-column prop="unit" label="单位" width="100" /><el-table-column label="采集时间" width="180"><template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template></el-table-column></el-table>
        <h4>安卓实例指标</h4>
        <el-table :data="androidMetrics" size="small" stripe><el-table-column prop="androidName" label="安卓实例" /><el-table-column prop="metricCode" label="指标" /><el-table-column prop="appPackage" label="应用包名" /><el-table-column prop="metricValue" label="当前值" /><el-table-column label="采集时间" width="180"><template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="安卓实例" name="androids">
        <el-table :data="androids" size="small" stripe><el-table-column prop="name" label="实例名称" /><el-table-column prop="status" label="状态"><template #default="{ row }"><el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="220"><template #default="{ row }"><el-button size="small" @click="submitOp('RUN_ANDROID', row.name)">启动</el-button><el-button size="small" @click="submitOp('STOP_ANDROID', row.name)">停止</el-button><el-button size="small" @click="submitOp('REBOOT_ANDROID', row.name)">重启</el-button></template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="最近告警" name="alarms"><el-table :data="alarms" size="small" stripe><el-table-column prop="ruleName" label="规则" /><el-table-column prop="metricValue" label="指标值" /><el-table-column prop="status" label="状态" /><el-table-column label="触发时间" width="180"><template #default="{ row }">{{ formatDateTime(row.firedAt) }}</template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="最近日志" name="logs"><el-table :data="logs" size="small" stripe><el-table-column prop="logLevel" label="级别" width="100" /><el-table-column prop="message" label="消息" /><el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="任务记录" name="tasks"><el-table :data="tasks" size="small" stripe><el-table-column prop="operationCode" label="操作" /><el-table-column prop="status" label="状态" /><el-table-column prop="resultMsg" label="结果" /><el-table-column label="完成时间" width="180"><template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deviceApi, metricBindingApi } from '@/api'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'
import { formatDateTime } from '@/utils/date'
import { filterAppliedMetrics } from '@/utils/device-metrics'

const deviceId = useRoute().params.id
const activeTab = ref('metrics'); const loading = ref(false); const device = reactive({}); const androids = ref([]); const bindings = ref([]); const latestMetrics = ref([]); const alarms = ref([]); const logs = ref([]); const tasks = ref([])
const appliedMetrics = computed(() => filterAppliedMetrics(latestMetrics.value, bindings.value))
const hostMetrics = computed(() => appliedMetrics.value.filter(item => item.targetType === 'HOST'))
const androidMetrics = computed(() => appliedMetrics.value.filter(item => item.targetType === 'ANDROID_INSTANCE'))
let timer
async function load() {
  loading.value = true
  try {
    const [detail, instances, hostBindings, metrics, alarmResult, logResult, taskResult] = await Promise.all([deviceApi.detail(deviceId), deviceApi.androids(deviceId), metricBindingApi.listHost(deviceId), deviceApi.latestMetrics(deviceId), deviceApi.alarms(deviceId, { page: 1, size: 20 }), deviceApi.logs(deviceId, { page: 1, size: 20 }), deviceApi.tasks(deviceId, { page: 1, size: 20 })])
    Object.assign(device, detail.data || {}); androids.value = instances.data || []; bindings.value = hostBindings.data || []
    const androidBindingResults = await Promise.all(androids.value.map(async instance => (await metricBindingApi.listAndroid(deviceId, instance.name)).data || []))
    bindings.value.push(...androidBindingResults.flat()); latestMetrics.value = metrics.data || []; alarms.value = alarmResult.data.records || []; logs.value = logResult.data.records || []; tasks.value = taskResult.data.records || []
  } finally { loading.value = false }
}
async function submitOp(operationCode, name) { await deviceApi.ops(deviceId, { operationCode, params: { name } }); ElMessage.success('任务已提交'); await load() }
onMounted(() => { load(); timer = window.setInterval(load, 10000) })
onUnmounted(() => window.clearInterval(timer))
</script>
