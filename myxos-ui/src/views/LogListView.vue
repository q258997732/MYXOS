<template>
  <div class="page-container">
    <h2 class="page-title">日志查询</h2>

    <div class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="动作类型">
          <el-select v-model="query.actionType" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="日志" value="LOG" />
            <el-option label="操作" value="OPERATION" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="query.logLevel" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="logs" size="small" stripe>
        <el-table-column prop="deviceId" label="设备ID" width="100" />
        <el-table-column prop="actionType" label="动作类型" width="120" />
        <el-table-column prop="logLevel" label="日志级别" width="100">
          <template #default="{ row }">
            <el-tag :type="logLevelType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160" />
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
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { logApi } from '@/api'

const logs = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ actionType: '', logLevel: '', page: 1, size: 20 })

const logLevelType = (level) => {
  switch (level) {
    case 'ERROR': return 'danger'
    case 'WARN': return 'warning'
    case 'INFO': return 'success'
    default: return 'info'
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await logApi.list(query)
    logs.splice(0, logs.length, ...(res.data.records || []))
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.page = 1
  load()
}

const reset = () => {
  query.actionType = ''
  query.logLevel = ''
  query.page = 1
  load()
}

onMounted(load)
</script>
