<template>
  <div class="page-container">
    <h2 class="page-title">告警列表</h2>

    <div class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="触发中" value="FIRING" />
            <el-option label="已恢复" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="query.deviceId" clearable placeholder="全部设备" filterable style="width: 220px">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.name || d.ip} (${d.ip}:${d.port})`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button type="danger" @click="clearAll">清空</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="content-card" shadow="never">
      <el-table :data="alarms" v-loading="loading" stripe size="default">
        <el-table-column prop="deviceName" label="设备" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则" />
        <el-table-column prop="metricValue" label="指标值" />
        <el-table-column prop="thresholdValue" label="阈值" />
        <el-table-column prop="firedAt" label="触发时间" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.firedAt) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'FIRING' ? 'danger' : 'success'" size="small">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'FIRING'" size="small" type="primary" @click="resolve(scope.row.id)">恢复</el-button>
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
          @change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alarmApi, deviceApi } from '@/api'
import { formatDateTime } from '@/utils/date'

const alarms = reactive([])
const devices = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: '', deviceId: null, page: 1, size: 20 })

async function loadDevices() {
  const res = await deviceApi.list({ page: 1, size: 1000 })
  devices.splice(0, devices.length, ...(res.data.records || []))
}

async function load() {
  loading.value = true
  try {
    const res = await alarmApi.list(query)
    alarms.splice(0, alarms.length, ...res.data.records)
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.status = ''
  query.deviceId = null
  query.page = 1
  load()
}

async function resolve(id) {
  await alarmApi.resolve(id)
  ElMessage.success('告警已恢复')
  load()
}

async function clearAll() {
  await ElMessageBox.confirm('确认清空所有告警记录？该操作不可恢复。', '清空告警', {
    type: 'warning',
    confirmButtonText: '清空',
    cancelButtonText: '取消'
  })
  await alarmApi.clear()
  ElMessage.success('告警已清空')
  query.page = 1
  load()
}

onMounted(() => {
  loadDevices()
  load()
})
</script>
