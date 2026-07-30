<template>
  <div>
    <h2>设备托管</h2>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card title="手动添加">
          <el-form :model="manualForm" label-width="100px">
            <el-form-item label="IP">
              <el-input v-model="manualForm.ip" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="manualForm.port" />
            </el-form-item>
            <el-form-item label="模式">
              <el-select v-model="manualForm.mode">
                <el-option label="桥接" value="BRIDGE" />
                <el-option label="NAT" value="NAT" />
              </el-select>
            </el-form-item>
            <el-form-item label="分组">
              <el-select v-model="manualForm.groupId">
                <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="manualForm.name" placeholder="留空自动读取" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="createDevice">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card title="网段发现">
          <el-form :model="discoverForm" label-width="100px">
            <el-form-item label="CIDR">
              <el-input v-model="discoverForm.cidr" placeholder="192.168.30.0/24" />
            </el-form-item>
            <el-form-item label="起始端口">
              <el-input-number v-model="discoverForm.portFrom" />
            </el-form-item>
            <el-form-item label="结束端口">
              <el-input-number v-model="discoverForm.portTo" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitDiscover">开始扫描</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card title="发现任务" class="mt-16">
      <el-table :data="tasks" size="small">
        <el-table-column prop="cidr" label="CIDR" />
        <el-table-column prop="portFrom" label="起始端口" />
        <el-table-column prop="portTo" label="结束端口" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="foundCount" label="发现数量" />
        <el-table-column prop="finishedAt" label="完成时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi, deviceGroupApi, discoverApi } from '@/api'

const groups = reactive([])
const manualForm = reactive({ ip: '', port: 9082, mode: 'BRIDGE', groupId: null, name: '' })
const discoverForm = reactive({ cidr: '', portFrom: 9082, portTo: 9082 })
const tasks = reactive([])

async function loadGroups() {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...res.data)
}

async function createDevice() {
  await deviceApi.create(manualForm)
  ElMessage.success('设备已添加')
}

async function submitDiscover() {
  await discoverApi.scan(discoverForm)
  ElMessage.success('扫描任务已提交')
  loadTasks()
}

async function loadTasks() {
  const res = await discoverApi.tasks()
  tasks.splice(0, tasks.length, ...res.data)
}

onMounted(() => {
  loadGroups()
  loadTasks()
})
</script>

<style scoped>
.mt-16 {
  margin-top: 16px;
}
</style>
