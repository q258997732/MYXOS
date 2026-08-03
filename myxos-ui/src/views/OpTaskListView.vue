<template>
  <div class="page-container">
    <h2 class="page-title">任务队列</h2>

    <div class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待执行" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.source" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="手动" value="MANUAL" />
            <el-option label="自动" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="tasks" size="small" stripe>
        <el-table-column prop="id" label="任务ID" width="90" />
        <el-table-column prop="deviceId" label="设备ID" width="90" />
        <el-table-column prop="operationCode" label="操作" width="140" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduledAt" label="计划时间" width="160" />
        <el-table-column prop="finishedAt" label="完成时间" width="160" />
        <el-table-column prop="resultMsg" label="结果" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'FAILED'" size="small" :icon="RefreshRight" @click="retry(row.id)">重试</el-button>
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
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, RefreshRight } from '@element-plus/icons-vue'
import { opTaskApi } from '@/api'

const tasks = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: '', source: '', page: 1, size: 20 })

const statusType = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await opTaskApi.list(query)
    tasks.splice(0, tasks.length, ...(res.data.records || []))
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
  query.status = ''
  query.source = ''
  query.page = 1
  load()
}

const retry = async (id) => {
  await opTaskApi.retry(id)
  ElMessage.success('已重试')
  load()
}

onMounted(load)
</script>
