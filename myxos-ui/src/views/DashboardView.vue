<template>
  <div class="page-container">
    <h2 class="page-title">仪表盘</h2>

    <!-- 资源概览 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card primary">
          <div class="stat-icon"><el-icon size="32"><Monitor /></el-icon></div>
          <div class="stat-info">
            <div class="stat-label">设备总数</div>
            <div class="stat-value">{{ stats.total }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card success">
          <div class="stat-icon"><el-icon size="32"><CircleCheck /></el-icon></div>
          <div class="stat-info">
            <div class="stat-label">在线设备</div>
            <div class="stat-value">{{ stats.online }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card danger">
          <div class="stat-icon"><el-icon size="32"><CircleClose /></el-icon></div>
          <div class="stat-info">
            <div class="stat-label">离线设备</div>
            <div class="stat-value">{{ stats.offline }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card warning">
          <div class="stat-icon"><el-icon size="32"><Warning /></el-icon></div>
          <div class="stat-info">
            <div class="stat-label">今日告警</div>
            <div class="stat-value">{{ stats.alarms }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图 + 健康度 -->
    <el-row :gutter="16" class="mt-16">
      <el-col :xs="24" :lg="16">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>近 7 天告警趋势</span>
            </div>
          </template>
          <div ref="alarmChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>设备健康度</span>
            </div>
          </template>
          <div ref="healthChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近事件 -->
    <el-row :gutter="16" class="mt-16">
      <el-col :xs="24" :lg="12">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最近告警</span>
              <el-link type="primary" @click="$router.push('/alarms')">查看更多</el-link>
            </div>
          </template>
          <el-table :data="recentAlarms" size="small" stripe>
            <el-table-column prop="deviceName" label="设备" show-overflow-tooltip />
            <el-table-column prop="metricName" label="指标" width="120" />
            <el-table-column prop="level" label="级别" width="80">
              <template #default="{ row }">
                <el-tag :type="row.level === 'CRITICAL' ? 'danger' : row.level === 'WARNING' ? 'warning' : 'info'" size="small">
                  {{ row.level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最近任务</span>
              <el-link type="primary" @click="$router.push('/op-tasks')">查看更多</el-link>
            </div>
          </template>
          <el-table :data="recentTasks" size="small" stripe>
            <el-table-column prop="operationCode" label="操作" width="140" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="taskStatusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scheduledAt" label="时间" width="160" />
            <el-table-column prop="resultMsg" label="结果" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { Monitor, CircleCheck, CircleClose, Warning } from '@element-plus/icons-vue'
import { deviceApi, alarmApi, opTaskApi } from '@/api'

const stats = reactive({ total: 0, online: 0, offline: 0, alarms: 0 })
const recentAlarms = reactive([])
const recentTasks = reactive([])
const alarmChartRef = ref(null)
const healthChartRef = ref(null)
let alarmChart = null
let healthChart = null

const taskStatusType = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    default: return 'info'
  }
}

const loadStats = async () => {
  const res = await deviceApi.list({ page: 1, size: 9999 })
  const records = res.data.records || []
  stats.total = res.data.total || 0
  stats.online = records.filter(d => d.status === 'ONLINE').length
  stats.offline = records.filter(d => d.status === 'OFFLINE').length
}

const loadAlarms = async () => {
  const res = await alarmApi.list({ page: 1, size: 5 })
  recentAlarms.splice(0, recentAlarms.length, ...(res.data.records || []))
  const today = new Date().toISOString().slice(0, 10)
  stats.alarms = (res.data.records || []).filter(a => a.createdAt && a.createdAt.startsWith(today)).length
}

const loadTasks = async () => {
  const res = await opTaskApi.list({ page: 1, size: 5 })
  recentTasks.splice(0, recentTasks.length, ...(res.data.records || []))
}

const initAlarmChart = () => {
  if (!alarmChartRef.value) return
  alarmChart = echarts.init(alarmChartRef.value)
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  alarmChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [{
      name: '告警数',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.2 },
      data: [5, 3, 8, 4, 6, 2, 7]
    }]
  })
}

const initHealthChart = () => {
  if (!healthChartRef.value) return
  healthChart = echarts.init(healthChartRef.value)
  healthChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      name: '设备状态',
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: false,
      label: { show: false },
      data: [
        { value: stats.online, name: '在线', itemStyle: { color: '#67c23a' } },
        { value: stats.offline, name: '离线', itemStyle: { color: '#f56c6c' } },
        { value: Math.max(0, stats.total - stats.online - stats.offline), name: '其他', itemStyle: { color: '#909399' } }
      ]
    }]
  })
}

const onResize = () => {
  alarmChart?.resize()
  healthChart?.resize()
}

onMounted(async () => {
  await loadStats()
  await loadAlarms()
  await loadTasks()
  initAlarmChart()
  initHealthChart()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  alarmChart?.dispose()
  healthChart?.dispose()
})
</script>

<style scoped>
.stats-row {
  margin-bottom: var(--spacing-md);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-radius: var(--border-radius);
  background: var(--card-bg);
  box-shadow: var(--shadow-sm);
  border-left: 4px solid #409eff;
}

.stat-card.primary { border-left-color: #409eff; }
.stat-card.success { border-left-color: #67c23a; }
.stat-card.danger { border-left-color: #f56c6c; }
.stat-card.warning { border-left-color: #e6a23c; }

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.stat-card.success .stat-icon { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.stat-card.danger .stat-icon { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.stat-card.warning .stat-icon { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }

.stat-label {
  color: var(--text-secondary);
  font-size: 14px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.chart-container {
  height: 280px;
}
</style>
