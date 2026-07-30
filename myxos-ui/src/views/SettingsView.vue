<template>
  <div>
    <h2>系统配置</h2>
    <el-table :data="configs" v-loading="loading">
      <el-table-column prop="configKey" label="配置键" />
      <el-table-column prop="configValue" label="配置值" />
      <el-table-column prop="description" label="说明" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button size="small" @click="edit(scope.row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="编辑配置">
      <el-form :model="editForm">
        <el-form-item label="配置值">
          <el-input v-model="editForm.configValue" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysConfigApi } from '@/api'

const configs = reactive([])
const loading = ref(false)
const dialogVisible = ref(false)
const editForm = reactive({ configKey: '', configValue: '' })

async function load() {
  loading.value = true
  try {
    const res = await sysConfigApi.list()
    configs.splice(0, configs.length, ...res.data)
  } finally {
    loading.value = false
  }
}

function edit(row) {
  editForm.configKey = row.configKey
  editForm.configValue = row.configValue
  dialogVisible.value = true
}

async function save() {
  await sysConfigApi.update(editForm.configKey, editForm.configValue)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

onMounted(load)
</script>
