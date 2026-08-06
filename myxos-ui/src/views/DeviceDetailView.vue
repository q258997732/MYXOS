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
      <el-tab-pane v-if="userStore.isAdmin" label="手动运维" name="operations">
        <el-form label-width="100px" class="operation-form">
          <el-form-item label="安卓实例"><el-select v-model="instanceName" filterable placeholder="选择实例"><el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" /></el-select></el-form-item>
          <el-form-item label="常用操作"><el-button @click="submitScreenshot">截图</el-button><el-button @click="openClipboard">获取剪贴板</el-button><el-button @click="openDialog('clipboard')">设置剪贴板</el-button><el-button @click="openDialog('language')">设置语言</el-button><el-button @click="openDialog('location')">刷新定位</el-button><el-button @click="openDialog('shell')">执行 Shell</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="最近告警" name="alarms"><el-table :data="alarms" size="small" stripe><el-table-column prop="ruleName" label="规则" /><el-table-column prop="metricValue" label="指标值" /><el-table-column prop="status" label="状态" /><el-table-column label="触发时间" width="180"><template #default="{ row }">{{ formatDateTime(row.firedAt) }}</template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="最近日志" name="logs"><el-table :data="logs" size="small" stripe><el-table-column prop="logLevel" label="级别" width="100" /><el-table-column prop="message" label="消息" /><el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="任务记录" name="tasks"><el-table :data="tasks" size="small" stripe><el-table-column prop="operationCode" label="操作" /><el-table-column prop="status" label="状态" /><el-table-column prop="resultMsg" label="结果" /><el-table-column label="完成时间" width="180"><template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
    <el-dialog v-model="screenshotVisible" title="设备截图" width="90%" destroy-on-close><img v-if="screenshotData" :src="screenshotData" class="screenshot" /></el-dialog>
    <el-dialog v-model="clipboardVisible" title="剪贴板内容" width="480px"><el-input v-model="clipboardData" type="textarea" :rows="6" readonly /></el-dialog>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px"><el-form><el-form-item v-if="dialogType === 'clipboard'" label="文本"><el-input v-model="dialogForm.text" type="textarea" /></el-form-item><el-form-item v-if="dialogType === 'language'" label="国家"><el-input v-model="dialogForm.country" /></el-form-item><el-form-item v-if="dialogType === 'language' || dialogType === 'location'" label="语言"><el-input v-model="dialogForm.language" /></el-form-item><el-form-item v-if="dialogType === 'shell'" label="Shell"><el-input v-model="dialogForm.command" type="textarea" :rows="4" /><pre v-if="dialogResult">{{ dialogResult }}</pre></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="dialogLoading" @click="confirmDialog">确定</el-button></template></el-dialog>
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
import { useUserStore } from '@/store'

const deviceId = useRoute().params.id
const userStore = useUserStore()
const activeTab = ref('metrics'); const loading = ref(false); const device = reactive({}); const androids = ref([]); const bindings = ref([]); const latestMetrics = ref([]); const alarms = ref([]); const logs = ref([]); const tasks = ref([])
const appliedMetrics = computed(() => filterAppliedMetrics(latestMetrics.value, bindings.value))
const hostMetrics = computed(() => appliedMetrics.value.filter(item => item.targetType === 'HOST'))
const androidMetrics = computed(() => appliedMetrics.value.filter(item => item.targetType === 'ANDROID_INSTANCE'))
let timer
const instanceName = ref(''); const screenshotVisible = ref(false); const screenshotData = ref(''); const clipboardVisible = ref(false); const clipboardData = ref(''); const dialogVisible = ref(false); const dialogType = ref(''); const dialogLoading = ref(false); const dialogResult = ref(''); const dialogForm = reactive({ text: '', country: 'cn', language: 'zh', command: '' })
const dialogTitle = computed(() => ({ clipboard: '设置剪贴板', language: '设置语言', location: '刷新定位', shell: '执行 Shell' }[dialogType.value] || ''))
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
function requireInstance() { if (instanceName.value) return true; ElMessage.warning('请选择安卓实例'); return false }
async function submitScreenshot() { if (!requireInstance()) return; const result = await deviceApi.screenshot(deviceId, { name: instanceName.value, level: 1 }); screenshotData.value = result.data || ''; screenshotVisible.value = true }
async function openClipboard() { if (!requireInstance()) return; const result = await deviceApi.clipboardGet(deviceId, { name: instanceName.value }); clipboardData.value = result.data || ''; clipboardVisible.value = true }
function openDialog(type) { if (!requireInstance()) return; dialogType.value = type; dialogResult.value = ''; dialogVisible.value = true }
async function confirmDialog() {
  dialogLoading.value = true
  try {
    if (dialogType.value === 'shell') { const result = await deviceApi.shell(deviceId, { name: instanceName.value, command: dialogForm.command }); dialogResult.value = typeof result.data === 'string' ? result.data : JSON.stringify(result.data); return }
    const params = { name: instanceName.value }
    const operationCode = dialogType.value === 'clipboard' ? 'SET_CLIPBOARD' : (dialogType.value === 'language' ? 'SET_LANGUAGE' : 'REFRESH_LOCATION')
    if (dialogType.value === 'clipboard') params.text = dialogForm.text
    if (dialogType.value === 'language') { params.country = dialogForm.country; params.language = dialogForm.language }
    if (dialogType.value === 'location') params.language = dialogForm.language
    await deviceApi.ops(deviceId, { operationCode, params }); dialogVisible.value = false; ElMessage.success('任务已提交')
  } finally { dialogLoading.value = false }
}
onMounted(() => { load(); timer = window.setInterval(load, 10000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<style scoped>
.operation-form { max-width: 760px; padding: 20px 0; }.screenshot { display: block; max-width: 100%; max-height: 75vh; margin: auto; } pre { white-space: pre-wrap; max-height: 300px; overflow: auto; }
</style>
