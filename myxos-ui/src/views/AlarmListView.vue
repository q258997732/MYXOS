<template>
  <div>
    <h2>告警列表</h2>
    <el-form :inline="true" :model="query">
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable>
          <el-option label="触发中" value="FIRING" />
          <el-option label="已恢复" value="RESOLVED" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备">
        <el-input-number v-model="query.deviceId" placeholder="设备ID" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="alarms">
      <el-table-column prop="deviceName" label="设备" />
      <el-table-column prop="ruleName" label="规则" />
      <el-table-column prop="metricValue" label="指标值" />
      <el-table-column prop="thresholdValue" label="阈值" />
      <el-table-column prop="firedAt" label="触发时间" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'FIRING'" size="small" @click="resolve(scope.row.id)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @change="load"
    />
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi } from '@/api'

const alarms = reactive([])
const total = reactive(0)
const query = reactive({ status: '', deviceId: null, page: 1, size: 20 })

async function load() {
  const res = await alarmApi.list(query)
  alarms.splice(0, alarms.length, ...res.data.records)
  total.value = res.data.total
}

function search() {
  query.page = 1
  load()
}

async function resolve(id) {
  await alarmApi.resolve(id)
  ElMessage.success('告警已恢复')
  load()
}

onMounted(load)
</script>
