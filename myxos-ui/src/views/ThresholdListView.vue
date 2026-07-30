<template>
  <div>
    <h2>阈值规则</h2>
    <el-button type="primary" @click="$router.push('/thresholds/edit')">新增规则</el-button>
    <el-table :data="rules" class="mt-16">
      <el-table-column prop="name" label="规则名称" />
      <el-table-column prop="metricType" label="指标类型" />
      <el-table-column label="条件">
        <template #default="scope">{{ scope.row.compareOp }} {{ scope.row.thresholdValue }}</template>
      </el-table-column>
      <el-table-column prop="scopeType" label="作用范围" />
      <el-table-column prop="enabled" label="启用">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" :active-value="1" :inactive-value="0" @change="toggle(scope.row.id)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" @click="edit(scope.row.id)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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

async function toggle(id) {
  await thresholdApi.toggle(id)
  ElMessage.success('状态已更新')
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

<style scoped>
.mt-16 {
  margin-top: 16px;
}
</style>
