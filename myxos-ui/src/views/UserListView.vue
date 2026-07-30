<template>
  <div>
    <h2>用户管理</h2>
    <el-button type="primary" @click="openDialog()">新增用户</el-button>
    <el-table :data="users" class="mt-16">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="role" label="角色" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">{{ scope.row.status === 1 ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
          <el-button size="small" @click="resetPassword(scope.row.id)">重置密码</el-button>
          <el-button size="small" @click="toggleStatus(scope.row.id)">{{ scope.row.status === 1 ? '禁用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role">
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
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'

const users = reactive([])
const dialogVisible = ref(false)
const form = reactive({ id: null, username: '', nickname: '', role: 'OPERATOR' })
const isEdit = computed(() => !!form.id)

async function load() {
  const res = await userApi.list()
  users.splice(0, users.length, ...res.data)
}

function openDialog(row) {
  if (row) {
    Object.assign(form, row)
  } else {
    form.id = null
    form.username = ''
    form.nickname = ''
    form.role = 'OPERATOR'
  }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) {
    await userApi.update(form.id, form)
  } else {
    await userApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function resetPassword(id) {
  await userApi.resetPassword(id)
  ElMessage.success('密码已重置')
}

async function toggleStatus(id) {
  await userApi.toggleStatus(id)
  load()
}

onMounted(load)
</script>

<style scoped>
.mt-16 {
  margin-top: 16px;
}
</style>
