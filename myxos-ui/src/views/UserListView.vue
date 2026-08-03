<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="users" size="small" stripe>
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
            <el-tag v-else-if="row.role === 'OPERATOR'" type="primary" size="small">操作员</el-tag>
            <el-tag v-else type="info" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" :icon="Key" @click="openResetDialog(row)">重置密码</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'info' : 'success'"
              :icon="row.status === 1 ? 'CircleClose' : 'CircleCheck'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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

    <!-- 新增/编辑用户 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="操作员" value="OPERATOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="400px">
      <el-form :model="resetForm" label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="resetForm.password" type="password" placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Key } from '@element-plus/icons-vue'
import { userApi } from '@/api'

const users = reactive([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20 })

const dialogVisible = ref(false)
const form = reactive({ id: null, username: '', nickname: '', role: 'OPERATOR', password: '' })
const isEdit = computed(() => !!form.id)

const resetDialogVisible = ref(false)
const resetForm = reactive({ id: null, password: '' })

const load = async () => {
  loading.value = true
  try {
    const res = await userApi.list(query)
    users.splice(0, users.length, ...(res.data.records || []))
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  if (row) {
    Object.assign(form, row)
    form.password = ''
  } else {
    form.id = null
    form.username = ''
    form.nickname = ''
    form.role = 'OPERATOR'
    form.password = ''
  }
  dialogVisible.value = true
}

const save = async () => {
  if (isEdit.value) {
    await userApi.update(form.id, {
      nickname: form.nickname,
      role: form.role
    })
  } else {
    await userApi.create({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      role: form.role
    })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

const openResetDialog = (row) => {
  resetForm.id = row.id
  resetForm.password = ''
  resetDialogVisible.value = true
}

const confirmReset = async () => {
  if (!resetForm.password) {
    ElMessage.warning('请输入新密码')
    return
  }
  await userApi.resetPassword(resetForm.id, resetForm.password)
  ElMessage.success('密码已重置')
  resetDialogVisible.value = false
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  await userApi.update(row.id, { status: newStatus })
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.page-header .page-title {
  margin-bottom: 0;
}
</style>
