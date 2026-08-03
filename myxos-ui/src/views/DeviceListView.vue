<template>
  <div class="page-container">
    <h2 class="page-title">设备列表</h2>

    <div class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="分组">
          <el-select v-model="query.groupId" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="名称/IP" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="$router.push('/devices/create')">新增设备</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-loading="loading" class="device-grid">
      <el-card
        v-for="device in devices"
        :key="device.id"
        class="device-card"
        shadow="hover"
      >
        <div class="device-card-header">
          <div class="device-name">{{ device.name || device.ip }}</div>
          <DeviceStatusTag :status="device.status" />
        </div>
        <div class="device-meta">
          <div class="meta-item">
            <el-icon><Monitor /></el-icon>
            <span>IP: {{ device.ip }}:{{ device.port }}</span>
          </div>
          <div class="meta-item">
            <el-icon><Collection /></el-icon>
            <span>分组: {{ groupName(device.groupId) }}</span>
          </div>
          <div class="meta-item">
            <el-icon><Timer /></el-icon>
            <span>模式: {{ device.mode || '-' }}</span>
          </div>
          <div v-if="device.remark" class="meta-item">
            <el-icon><Document /></el-icon>
            <span :title="device.remark">{{ device.remark }}</span>
          </div>
        </div>
        <div class="device-actions">
          <el-button size="small" :icon="View" @click="goDetail(device.id)">详情</el-button>
          <el-button size="small" :icon="Edit" @click="$router.push(`/devices/${device.id}/edit`)">编辑</el-button>
          <el-button size="small" :icon="Bell" @click="openThreshold(device)">阈值</el-button>
          <el-button size="small" type="danger" plain :icon="Delete" @click="remove(device)">删除</el-button>
        </div>
      </el-card>
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="load"
      />
    </div>

    <DeviceThresholdDrawer v-model="drawerVisible" :device="selectedDevice" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, Plus, View, Edit, Delete, Bell,
  Monitor, Collection, Timer, Document
} from '@element-plus/icons-vue'
import { deviceApi, deviceGroupApi } from '@/api'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'
import DeviceThresholdDrawer from '@/components/DeviceThresholdDrawer.vue'

const router = useRouter()
const loading = ref(false)
const devices = reactive([])
const groups = reactive([])
const total = ref(0)
const drawerVisible = ref(false)
const selectedDevice = ref(null)

const query = reactive({
  groupId: '',
  status: '',
  keyword: '',
  page: 1,
  size: 12
})

const load = async () => {
  loading.value = true
  try {
    const res = await deviceApi.list(query)
    devices.splice(0, devices.length, ...(res.data.records || []))
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
  query.groupId = ''
  query.status = ''
  query.keyword = ''
  query.page = 1
  load()
}

const loadGroups = async () => {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...(res.data || []))
}

const groupName = (id) => {
  const g = groups.find(x => x.id === id)
  return g ? g.name : '-'
}

const openThreshold = (device) => {
  selectedDevice.value = device
  drawerVisible.value = true
}

const goDetail = (id) => {
  router.push(`/devices/${id}`)
}

const remove = async (device) => {
  try {
    await ElMessageBox.confirm(`确认删除设备 "${device.name || device.ip}"？`, '提示', { type: 'warning' })
    await deviceApi.delete(device.id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  loadGroups()
  load()
})
</script>

<style scoped>
.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-md);
}

.device-card {
  border-radius: var(--border-radius);
  transition: transform 0.2s;
}

.device-card:hover {
  transform: translateY(-2px);
}

.device-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.device-name {
  font-size: 16px;
  font-weight: 600;
}

.device-meta {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: var(--spacing-md);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
  padding: var(--spacing-md) 0;
}
</style>
