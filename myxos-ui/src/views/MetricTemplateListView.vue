<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">模板管理</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/metric-templates/create')">新建模板</el-button>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="templates" stripe>
        <el-table-column prop="name" label="模板名称" min-width="200" />
        <el-table-column label="目标类型" width="140">
          <template #default="{ row }"><el-tag size="small">{{ targetTypeLabel(row.targetType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="whenModified" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit" @click="$router.push(`/metric-templates/${row.id}/edit`)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { metricTemplateApi } from '@/api'

const templates = ref([])
const loading = ref(false)
const targetTypeLabel = (value) => value === 'HOST' ? '主机' : '安卓实例'

async function load() {
  loading.value = true
  try {
    const response = await metricTemplateApi.list()
    templates.value = response.data || []
  } finally {
    loading.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除模板“${row.name}”？`, '删除模板', { type: 'warning' })
  await metricTemplateApi.delete(row.id)
  ElMessage.success('模板已删除')
  load()
}

onMounted(load)
</script>
