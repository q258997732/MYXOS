<template>
  <div class="page-container">
    <h2 class="page-title">系统配置</h2>

    <el-card class="content-card" shadow="never" v-loading="loading">
      <el-table :data="configs" stripe size="default">
        <el-table-column prop="configKey" label="配置项" min-width="200">
          <template #default="scope">
            <div class="config-key">
              <el-tag size="small" effect="plain">{{ scope.row.configKey }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="configValue" label="当前值" min-width="200">
          <template #default="scope">
            <span class="config-value">{{ scope.row.configValue }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="说明" min-width="280">
          <template #default="scope">
            <span class="config-desc">{{ scope.row.description || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" :icon="Edit" @click="edit(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="编辑配置" width="500px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="配置项">
          <el-input v-model="editForm.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="editForm.configValue" placeholder="请输入新的配置值" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="2" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit } from '@element-plus/icons-vue'
import { sysConfigApi } from '@/api'

const configs = reactive([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editForm = reactive({ configKey: '', configValue: '', description: '' })

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
  editForm.description = row.description || ''
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await sysConfigApi.update(editForm.configKey, editForm.configValue)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.config-key {
  font-family: 'Courier New', monospace;
}
.config-value {
  font-family: 'Courier New', monospace;
  color: var(--text-primary);
  font-weight: 500;
}
.config-desc {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
