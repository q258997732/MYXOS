<template>
  <div class="page-container">
    <h2 class="page-title">{{ isEdit ? '编辑规则' : '新增规则' }}</h2>

    <el-card class="content-card" shadow="never" v-loading="loading">
      <el-form :model="form" label-width="110px" style="max-width: 720px">
        <!-- 基础规则 -->
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="例如 CPU 使用率过高" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item label="指标类型" required>
          <el-select v-model="form.metricType" style="width: 100%">
            <el-option-group label="性能指标">
              <el-option v-for="t in numericMetricTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-option-group>
            <el-option-group label="状态指标">
              <el-option v-for="t in statusMetricTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-option-group>
          </el-select>
        </el-form-item>

        <el-form-item label="触发条件" required>
          <div class="condition-row">
            <el-select v-model="form.compareOp" style="width: 120px">
              <el-option label="大于" value="GT" />
              <el-option label="大于等于" value="GTE" />
              <el-option label="小于" value="LT" />
              <el-option label="小于等于" value="LTE" />
              <el-option label="等于" value="EQ" />
              <el-option label="不等于" value="NE" />
            </el-select>
            <el-input-number v-model="form.thresholdValue" :precision="2" style="flex: 1; margin-left: 8px" />
            <span class="unit-text">{{ metricUnit(form.metricType) }}</span>
          </div>
        </el-form-item>

        <el-form-item label="触发模式" required>
          <el-radio-group v-model="form.triggerMode">
            <el-radio-button label="DURATION">持续时长</el-radio-button>
            <el-radio-button label="CONSECUTIVE">连续次数</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="持续秒数" v-if="form.triggerMode === 'DURATION'">
          <el-input-number v-model="form.durationSec" :min="0" style="width: 180px" />
          <span class="hint-text">0 表示即时触发</span>
        </el-form-item>

        <el-form-item label="连续次数" v-if="form.triggerMode === 'CONSECUTIVE'">
          <el-input-number v-model="form.consecutiveCount" :min="2" style="width: 180px" />
        </el-form-item>

        <!-- 作用范围 -->
        <el-form-item label="作用范围" required>
          <el-radio-group v-model="form.scopeType">
            <el-radio-button label="ALL">全部设备</el-radio-button>
            <el-radio-button label="GROUP">指定分组</el-radio-button>
            <el-radio-button label="DEVICE">指定设备</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="选择分组" v-if="form.scopeType === 'GROUP'" required>
          <el-select v-model="form.scopeId" placeholder="请选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="选择设备" v-if="form.scopeType === 'DEVICE'" required>
          <el-select v-model="form.scopeId" placeholder="请选择设备" filterable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.name || d.ip} (${d.ip}:${d.port})`" :value="d.id" />
          </el-select>
        </el-form-item>

        <!-- 动作配置 -->
        <el-divider content-position="left">动作配置</el-divider>

        <div v-for="(action, index) in form.actions" :key="index" class="action-card">
          <el-card shadow="never">
            <template #header>
              <div class="action-header">
                <span>动作 #{{ index + 1 }}</span>
                <el-button type="danger" link size="small" @click="removeAction(index)">删除</el-button>
              </div>
            </template>

            <el-form-item label="动作类型" required>
              <el-radio-group v-model="action.actionType" size="small">
                <el-radio-button label="LOG">记录日志</el-radio-button>
                <el-radio-button label="OPERATION">执行操作</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="日志级别" v-if="action.actionType === 'LOG'">
              <el-select v-model="action.logLevel" placeholder="请选择日志级别" style="width: 100%">
                <el-option label="DEBUG" value="DEBUG" />
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="ERROR" value="ERROR" />
              </el-select>
            </el-form-item>

            <el-form-item label="执行操作" v-if="action.actionType === 'OPERATION'">
              <el-select v-model="action.operationCode" placeholder="请选择操作" style="width: 100%">
                <el-option-group v-for="group in operationGroups" :key="group.label" :label="group.label">
                  <el-option v-for="op in group.options" :key="op.value" :label="op.label" :value="op.value" />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item label="操作参数" v-if="action.actionType === 'OPERATION'">
              <el-input
                v-model="action.operationParams"
                type="textarea"
                :rows="3"
                placeholder='根据执行操作填写 JSON 参数，例如：&#10;启动/停止/重启/重置实例：{"name":"container_01"}&#10;重命名：{"name":"old","newName":"new"}&#10;设置剪贴板：{"name":"c1","text":"hello"}&#10;设置语言：{"name":"c1","country":"cn","language":"zh"}&#10;IP 定位：{"name":"c1","language":"zh"}&#10;执行命令：{"name":"c1","command":"pm list packages"}&#10;截图：{"name":"c1"}'
              />
            </el-form-item>

            <el-form-item label="执行顺序">
              <el-input-number v-model="action.sort" :min="0" style="width: 120px" />
            </el-form-item>
          </el-card>
        </div>

        <el-button type="primary" plain :icon="Plus" @click="addAction" class="add-action-btn">添加动作</el-button>

        <el-form-item class="footer-actions">
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
import { Plus } from '@element-plus/icons-vue'
import { thresholdApi, deviceApi, deviceGroupApi } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)

const numericMetricTypes = [
  { value: 'CPU', label: 'CPU 使用率' },
  { value: 'MEM', label: '内存使用率' },
  { value: 'DISK', label: '磁盘使用率' },
  { value: 'NET_RX', label: '网络接收速率' },
  { value: 'NET_TX', label: '网络发送速率' },
  { value: 'TEMP', label: '温度' },
  { value: 'CUSTOM', label: '自定义指标' }
]

const statusMetricTypes = [
  { value: 'ONLINE', label: '设备在线' },
  { value: 'OFFLINE', label: '设备离线' }
]

const operationGroups = [
  {
    label: '主机级',
    options: [
      { value: 'REBOOT_HOST', label: '重启主机' }
    ]
  },
  {
    label: '容器生命周期',
    options: [
      { value: 'RUN_ANDROID', label: '启动安卓容器' },
      { value: 'STOP_ANDROID', label: '停止安卓容器' },
      { value: 'REBOOT_ANDROID', label: '重启安卓容器' },
      { value: 'RESET_ANDROID', label: '重置安卓容器' },
      { value: 'RENAME_ANDROID', label: '重命名容器' }
    ]
  },
  {
    label: '安卓实例操作',
    options: [
      { value: 'SET_CLIPBOARD', label: '设置剪贴板' },
      { value: 'GET_CLIPBOARD', label: '获取剪贴板' },
      { value: 'SET_LANGUAGE', label: '设置系统语言' },
      { value: 'REFRESH_LOCATION', label: 'IP 智能定位' },
      { value: 'SCREENSHOT', label: '设备截图' },
      { value: 'SHELL_ADB', label: '执行 Adb 命令' }
    ]
  }
]

const groups = reactive([])
const devices = reactive([])

const form = reactive({
  name: '',
  metricType: 'CPU',
  compareOp: 'GT',
  thresholdValue: 80,
  triggerMode: 'DURATION',
  durationSec: 60,
  consecutiveCount: 2,
  scopeType: 'ALL',
  scopeId: null,
  actions: []
})

const metricUnit = (type) => {
  switch (type) {
    case 'CPU':
    case 'MEM':
    case 'DISK':
      return '%'
    case 'NET_RX':
    case 'NET_TX':
      return 'KB/s'
    case 'TEMP':
      return '°C'
    default:
      return ''
  }
}

const addAction = () => {
  form.actions.push({
    actionType: 'LOG',
    logLevel: 'INFO',
    operationCode: '',
    operationParams: '{}',
    sort: form.actions.length
  })
}

const removeAction = (index) => {
  form.actions.splice(index, 1)
}

const loadGroups = async () => {
  const res = await deviceGroupApi.list()
  groups.splice(0, groups.length, ...(res.data || []))
}

const loadDevices = async () => {
  const res = await deviceApi.list({ page: 1, size: 1000 })
  devices.splice(0, devices.length, ...(res.data.records || []))
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await thresholdApi.detail(route.params.id)
    const data = res.data || {}
    const rule = data.rule || {}
    Object.assign(form, {
      name: rule.name || '',
      metricType: rule.metricType || 'CPU',
      compareOp: rule.compareOp || 'GT',
      thresholdValue: rule.thresholdValue != null ? Number(rule.thresholdValue) : 80,
      triggerMode: rule.triggerMode || 'DURATION',
      durationSec: rule.durationSec || 60,
      consecutiveCount: rule.consecutiveCount || 2,
      scopeType: rule.scopeType || 'ALL',
      scopeId: rule.scopeId || null
    })
    form.actions.splice(0, form.actions.length)
    const actions = data.actions || []
    if (actions.length > 0) {
      actions.forEach(a => {
        form.actions.push({
          actionType: a.actionType || 'LOG',
          logLevel: a.logLevel || 'INFO',
          operationCode: a.operationCode || '',
          operationParams: a.operationParams || '{}',
          sort: a.sort || 0
        })
      })
    } else {
      addAction()
    }
  } finally {
    loading.value = false
  }
}

const validate = () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return false
  }
  if (!form.metricType) {
    ElMessage.warning('请选择指标类型')
    return false
  }
  if (form.scopeType === 'GROUP' && !form.scopeId) {
    ElMessage.warning('请选择分组')
    return false
  }
  if (form.scopeType === 'DEVICE' && !form.scopeId) {
    ElMessage.warning('请选择设备')
    return false
  }
  return true
}

const save = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload = {
      name: form.name,
      metricType: form.metricType,
      compareOp: form.compareOp,
      thresholdValue: form.thresholdValue,
      triggerMode: form.triggerMode,
      durationSec: form.durationSec,
      consecutiveCount: form.consecutiveCount,
      scopeType: form.scopeType,
      scopeId: form.scopeType === 'ALL' ? null : form.scopeId,
      actions: form.actions
    }
    if (isEdit.value) {
      await thresholdApi.update(route.params.id, payload)
    } else {
      await thresholdApi.create(payload)
    }
    ElMessage.success('保存成功')
    router.push('/thresholds')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadGroups()
  loadDevices()
  if (isEdit.value) {
    loadDetail()
  } else {
    if (form.actions.length === 0) addAction()
  }
})
</script>

<style scoped>
.condition-row {
  display: flex;
  align-items: center;
}
.unit-text {
  margin-left: 8px;
  color: var(--text-secondary);
  min-width: 50px;
}
.hint-text {
  margin-left: 8px;
  color: var(--text-muted);
  font-size: 13px;
}
.action-card {
  margin-bottom: var(--spacing-md);
}
.action-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.add-action-btn {
  margin-bottom: var(--spacing-md);
}
.footer-actions {
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid #ebeef5;
}
</style>
