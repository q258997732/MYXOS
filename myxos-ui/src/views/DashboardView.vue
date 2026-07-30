<template>
  <div>
    <h2>仪表盘</h2>
    <el-row :gutter="16" class="cards">
      <el-col :span="6">
        <el-statistic title="设备总数" :value="stats.total" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="在线" :value="stats.online" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="离线" :value="stats.offline" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="今日告警" :value="stats.alarms" />
      </el-col>
    </el-row>

    <el-card title="最近告警" class="section">
      <el-table :data="recentAlarms" size="small">
        <el-table-column prop="deviceName" label="设备" />
        <el-table-column prop="ruleName" label="规则" />
        <el-table-column prop="metricValue" label="指标值" />
        <el-table-column prop="firedAt" label="触发时间" />
      </el-table>
    </el-card>

    <el-card title="最近任务" class="section">
      <el-table :data="recentTasks" size="small">
        <el-table-column prop="id" label="任务 ID" />
        <el-table-column prop="operationCode" label="操作" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="finishedAt" label="完成时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { deviceApi, alarmApi, opTaskApi } from '@/api'

const stats = reactive({ total: 0, online: 0, offline: 0, alarms: 0 })
const recentAlarms = reactive([])
const recentTasks = reactive([])

async function loadData() {
  try {
    const deviceRes = await deviceApi.list({ page: 1, size: 1 })
    const devices = deviceRes.data.records || []
    stats.total = deviceRes.data.total || 0
    stats.online = devices.filter(d => d.status === 'ONLINE').length
    stats.offline = devices.filter(d => d.status === 'OFFLINE').length

    const alarmRes = await alarmApi.list({ page: 1, size: 5 })
    recentAlarms.splice(0, recentAlarms.length, ...alarmRes.data.records)

    const taskRes = await opTaskApi.list({ page: 1, size: 5 })
    recentTasks.splice(0, recentTasks.length, ...taskRes.data.records)
  } catch (e) {
    // 错误已在拦截器提示
  }
}

onMounted(loadData)
</script>

<style scoped>
.cards {
  margin-bottom: 16px;
}
.section {
  margin-bottom: 16px;
}
</style>
