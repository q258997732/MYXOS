<template>
  <div>
    <h2>设备列表</h2>
    <el-card class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="分组">
          <el-select v-model="query.groupId" placeholder="全部" clearable>
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="未知" value="UNKNOWN" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="名称/IP" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="devices" v-loading="loading">
      <el-table-column prop="name" label="设备名称">
        <template #default="scope">
          <el-link type="primary" @click="goDetail(scope.row.id)">{{ scope.row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" />
      <el-table-column prop="port" label="端口" />
      <el-table-column prop="mode" label="模式" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <DeviceStatusTag :status="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" />
      <el-table-column prop="lastSeenAt" label="最后在线" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" @click="goDetail(scope.row.id)">查看</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceApi, deviceGroupApi } from '@/api'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'

const router = useRouter()
const loading = reactive(false)
const devices = reactive([])
const groups = reactive([])
const total = reactive(0)
const query = reactive({ groupId: null, status: '', keyword: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const res = await deviceApi.list(query)
    devices.splice(0, devices.length, ...res.data.records)
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...res.data)
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.groupId = null
  query.status = ''
  query.keyword = ''
  query.page = 1
  load()
}

function goDetail(id) {
  router.push(`/devices/${id}`)
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该设备？', '提示', { type: 'warning' })
    await deviceApi.delete(id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  load()
  loadGroups()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
</style>
