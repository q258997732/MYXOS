<template>
  <div class="page-container">
    <h2 class="page-title">阈值规则</h2>

    <div class="filter-card">
      <el-button type="primary" :icon="Plus" @click="$router.push('/thresholds/edit')">新增规则</el-button>
    </div>

    <el-card class="content-card" shadow="never">
      <el-table :data="rules" stripe size="default">
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="metricType" label="指标类型" width="120" />
        <el-table-column label="条件" width="160">
          <template #default="scope">
            <span>{{ opText(scope.row.compareOp) }} {{ scope.row.thresholdValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="作用范围" width="160">
          <template #default="scope">
            <span>{{ scopeText(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="100">
          <template #default="scope">
            <el-switch
              v-model="scope.row.enabled"
              :active-value="1"
              :inactive-value="0"
              @change="val => toggle(scope.row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" :icon="Edit" @click="edit(scope.row.id)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="remove(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { thresholdApi } from '@/api'

const router = useRouter()
const rules = reactive([])

async function load() {
  const res = await thresholdApi.list({ page: 1, size: 1000 })
  rules.splice(0, rules.length, ...res.data.records)
}

function edit(id) {
  router.push(`/thresholds/edit/${id}`)
}

function opText(op) {
  const map = {
    GT: '大于',
    GTE: '大于等于',
    LT: '小于',
    LTE: '小于等于',
    EQ: '等于',
    NE: '不等于'
  }
  return map[op] || op
}

function scopeText(row) {
  if (row.scopeType === 'ALL') return '全部设备'
  if (row.scopeType === 'GROUP') return `分组 ${row.scopeId}`
  if (row.scopeType === 'DEVICE') return `设备 ${row.scopeId}`
  return row.scopeType
}

async function toggle(row, enabled) {
  const original = row.enabled
  try {
    await thresholdApi.toggle(row.id, enabled)
    ElMessage.success('状态已更新')
  } catch (e) {
    row.enabled = original
    ElMessage.error('状态更新失败')
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该规则？', '提示', { type: 'warning' })
    await thresholdApi.delete(id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {}
}

onMounted(load)
</script>
