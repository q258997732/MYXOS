<template>
  <div class="page-container">
    <h2 class="page-title">{{ isEdit ? '编辑设备' : '新增设备' }}</h2>

    <el-card class="content-card" shadow="never" v-loading="loading">
      <el-form :model="form" label-width="100px" style="max-width: 600px">
        <el-form-item label="设备名称" required>
          <el-input v-model="form.name" placeholder="请输入设备名称" maxlength="64" show-word-limit />
        </el-form-item>

        <template v-if="!isEdit">
          <el-form-item label="IP 地址" required>
            <el-input v-model="form.ip" placeholder="例如 192.168.1.10" />
          </el-form-item>
          <el-form-item label="端口" required>
            <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
          <el-form-item label="网络模式" required>
            <el-select v-model="form.mode" style="width: 100%">
              <el-option label="桥接" value="BRIDGE" />
              <el-option label="NAT" value="NAT" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="IP:Port">
            <span class="readonly-text">{{ form.ip }}:{{ form.port }}</span>
          </el-form-item>
          <el-form-item label="网络模式">
            <span class="readonly-text">{{ form.mode === 'NAT' ? 'NAT' : '桥接' }}</span>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="在线" value="ONLINE" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="未知" value="UNKNOWN" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item label="分组">
          <el-select v-model="form.groupId" clearable placeholder="请选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="save" :loading="saving">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deviceApi, deviceGroupApi } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)

const groups = reactive([])
const form = reactive({
  name: '',
  ip: '',
  port: 9082,
  mode: 'BRIDGE',
  groupId: null,
  status: 'UNKNOWN',
  remark: ''
})

const loadGroups = async () => {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...(res.data || []))
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await deviceApi.detail(route.params.id)
    const data = res.data || {}
    Object.assign(form, {
      name: data.name || '',
      ip: data.ip || '',
      port: data.port || 9082,
      mode: data.mode || 'BRIDGE',
      groupId: data.groupId || null,
      status: data.status || 'UNKNOWN',
      remark: data.remark || ''
    })
  } finally {
    loading.value = false
  }
}

const validate = () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入设备名称')
    return false
  }
  if (!isEdit.value) {
    if (!form.ip || !/^((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)$/.test(form.ip)) {
      ElMessage.warning('请输入正确的 IPv4 地址')
      return false
    }
    if (!form.port || form.port < 1 || form.port > 65535) {
      ElMessage.warning('端口范围应为 1-65535')
      return false
    }
    if (!form.mode) {
      ElMessage.warning('请选择网络模式')
      return false
    }
  }
  return true
}

const save = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (isEdit.value) {
      await deviceApi.update(route.params.id, {
        name: form.name,
        groupId: form.groupId,
        status: form.status,
        remark: form.remark
      })
    } else {
      await deviceApi.create({
        name: form.name,
        ip: form.ip,
        port: form.port,
        mode: form.mode,
        groupId: form.groupId,
        remark: form.remark
      })
    }
    ElMessage.success('保存成功')
    router.push('/devices')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadGroups()
  if (isEdit.value) {
    loadDetail()
  }
})
</script>

<style scoped>
.readonly-text {
  color: var(--text-secondary);
  line-height: 32px;
}
</style>
