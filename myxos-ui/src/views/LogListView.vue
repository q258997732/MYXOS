<template>
  <div>
    <h2>日志查询</h2>
    <el-form :inline="true" :model="query">
      <el-form-item label="设备">
        <el-input-number v-model="query.deviceId" placeholder="设备ID" />
      </el-form-item>
      <el-form-item label="动作类型">
        <el-select v-model="query.actionType" clearable>
          <el-option label="日志" value="LOG" />
          <el-option label="操作" value="OPERATION" />
        </el-select>
      </el-form-item>
      <el-form-item label="日志级别">
        <el-select v-model="query.logLevel" clearable>
          <el-option label="DEBUG" value="DEBUG" />
          <el-option label="INFO" value="INFO" />
          <el-option label="WARN" value="WARN" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="logs">
      <el-table-column prop="deviceId" label="设备ID" />
      <el-table-column prop="actionType" label="动作类型" />
      <el-table-column prop="logLevel" label="日志级别" />
      <el-table-column prop="message" label="消息" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" />
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
import { logApi } from '@/api'

const logs = reactive([])
const total = reactive(0)
const query = reactive({ deviceId: null, actionType: '', logLevel: '', page: 1, size: 20 })

async function load() {
  const res = await logApi.list(query)
  logs.splice(0, logs.length, ...res.data.records)
  total.value = res.data.total
}

function search() {
  query.page = 1
  load()
}

onMounted(load)
</script>
