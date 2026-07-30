<template>
  <div>
    <h2>任务队列</h2>
    <el-form :inline="true" :model="query">
      <el-form-item label="设备">
        <el-input-number v-model="query.deviceId" placeholder="设备ID" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable>
          <el-option label="待执行" value="PENDING" />
          <el-option label="执行中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="query.source" clearable>
          <el-option label="手动" value="MANUAL" />
          <el-option label="自动" value="AUTO" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tasks">
      <el-table-column prop="id" label="任务ID" />
      <el-table-column prop="deviceId" label="设备ID" />
      <el-table-column prop="operationCode" label="操作" />
      <el-table-column prop="source" label="来源" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="scheduledAt" label="计划时间" />
      <el-table-column prop="finishedAt" label="完成时间" />
      <el-table-column prop="resultMsg" label="结果" show-overflow-tooltip />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'FAILED'" size="small" @click="retry(scope.row.id)">重试</el-button>
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
import { opTaskApi } from '@/api'

const tasks = reactive([])
const total = reactive(0)
const query = reactive({ deviceId: null, status: '', source: '', page: 1, size: 20 })

async function load() {
  const res = await opTaskApi.list(query)
  tasks.splice(0, tasks.length, ...res.data.records)
  total.value = res.data.total
}

function search() {
  query.page = 1
  load()
}

async function retry(id) {
  await opTaskApi.retry(id)
  ElMessage.success('已重试')
  load()
}

onMounted(load)
</script>
