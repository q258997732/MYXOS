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
          <el-select v-model="form.metricCode" style="width: 100%" @change="onMetricChange">
            <el-option v-for="item in metricCatalogs" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="触发条件" required>
          <div v-if="selectedValueType === 'ENUM'" class="condition-row">
            <el-select v-model="form.compareOp" style="width: 120px"><el-option label="属于" value="IN" /><el-option label="不属于" value="NOT_IN" /></el-select>
            <el-select v-model="form.thresholdOptions" multiple placeholder="选择已验证枚举值" style="flex: 1; margin-left: 8px"><el-option v-for="option in enumOptions" :key="option" :label="option" :value="option" /></el-select>
          </div>
          <div v-else-if="selectedValueType === 'STRING'" class="condition-row">
            <el-select v-model="form.compareOp" style="width: 120px"><el-option label="等于" value="EQ" /><el-option label="不等于" value="NE" /><el-option label="包含" value="CONTAINS" /></el-select>
            <el-input v-model="form.thresholdText" placeholder="目标文本" maxlength="255" style="flex: 1; margin-left: 8px" />
          </div>
          <div v-else class="condition-row">
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
          <el-select v-model="form.scopeIds" multiple collapse-tags collapse-tags-tooltip
            placeholder="请选择设备（可多选）" filterable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.name || d.ip} (${d.ip}:${d.port})`" :value="d.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="实例名称" v-if="form.metricType === 'ANDROID_STATUS'">
          <div class="android-name-field">
            <el-select v-model="form.scopeAndroidNames" multiple filterable allow-create default-first-option
              collapse-tags collapse-tags-tooltip :loading="androidNamesLoading"
              placeholder="留空表示作用范围内的全部实例" style="width: 100%">
              <el-option v-for="n in androidNameOptions" :key="n" :label="n" :value="n" />
            </el-select>
            <div class="hint-text hint-block">列出作用范围内主机采集到过的实例，也可手动输入；选择后仅监控这些实例</div>
          </div>
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
              <el-select v-model="action.operationCode" placeholder="请选择操作" style="width: 100%"
                @change="onOperationCodeChange(action)">
                <el-option-group v-for="group in operationGroups" :key="group.label" :label="group.label">
                  <el-option v-for="op in group.options" :key="op.value" :label="op.label" :value="op.value" />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item v-if="action.actionType === 'OPERATION'">
              <template #label>
                <el-button type="primary" link :icon="QuestionFilled" @click="paramHelpVisible = true">参数示例</el-button>
              </template>
              <div class="android-name-field">
                <el-input
                  v-model="action.operationParams"
                  type="textarea"
                  :rows="3"
                  :placeholder="paramPlaceholder(action.operationCode)"
                />
                <div class="hint-text hint-block" v-if="form.metricType === 'ANDROID_STATUS'">
                  name 留空或填 ${name} 时，自动替换为触发告警的实例名（哪个实例状态异常就操作哪个实例）
                </div>
              </div>
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

    <!-- 操作参数示例弹窗 -->
    <el-dialog v-model="paramHelpVisible" title="操作参数示例" width="680px" align-center append-to-body>
      <el-alert type="info" :closable="false" show-icon class="param-help-tip"
        title="操作参数为 JSON 字符串；name 为安卓容器名称，REBOOT_HOST 等主机级操作无需参数填 {} 即可；阈值自动触发时 name 留空或填 ${name} 会自动替换为触发告警的实例名" />
      <el-table :data="paramHelpList" size="small" stripe>
        <el-table-column prop="label" label="执行操作" width="130" />
        <el-table-column prop="example" label="参数 JSON 示例" min-width="260">
          <template #default="{ row }">
            <code class="param-example">{{ row.example }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="desc" label="参数说明" min-width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, QuestionFilled } from '@element-plus/icons-vue'
import { thresholdApi, deviceApi, deviceGroupApi, metricCatalogApi } from '@/api'
import { toThresholdForm } from '@/utils/threshold-form'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)
const metricCatalogs = ref([])
const enumOptions = ref([])

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
  { value: 'OFFLINE', label: '设备离线' },
  { value: 'ANDROID_ONLINE', label: '安卓实例在线数' },
  { value: 'ANDROID_OFFLINE', label: '安卓实例离线数' },
  { value: 'ANDROID_STATUS', label: '安卓实例状态' }
]

/** 状态类指标：支持"无需条件（检测到即触发）" */
const STATE_METRIC_TYPES = ['ONLINE', 'OFFLINE', 'ANDROID_ONLINE', 'ANDROID_OFFLINE']
/** 字符类指标：按字符串比较 */
const STRING_METRIC_TYPES = ['ANDROID_STATUS']

/** 安卓实例状态选项（采集侧统一归一化为这四个值） */
const androidStatusOptions = [
  { value: 'RUNNING', label: '运行中 (RUNNING)' },
  { value: 'STOPPED', label: '已停止 (STOPPED)' },
  { value: 'TRANSITION', label: '过渡中 (TRANSITION)' },
  { value: 'UNKNOWN', label: '未知 (UNKNOWN)' }
]

const METRIC_LABELS = {}
;[...numericMetricTypes, ...statusMetricTypes].forEach(t => { METRIC_LABELS[t.value] = t.label })

const metricLabel = (type) => METRIC_LABELS[type] || type

const isStateMetric = computed(() => STATE_METRIC_TYPES.includes(form.metricType))
const isStringMetric = computed(() => STRING_METRIC_TYPES.includes(form.metricType))

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
const paramHelpVisible = ref(false)

/** 每种执行操作的参数示例与说明 */
const OPERATION_PARAM_EXAMPLES = {
  REBOOT_HOST: { desc: '主机级操作，无需参数', example: '{}' },
  RUN_ANDROID: { desc: 'name：容器名称', example: '{"name":"container_01"}' },
  STOP_ANDROID: { desc: 'name：容器名称', example: '{"name":"container_01"}' },
  REBOOT_ANDROID: { desc: 'name：容器名称', example: '{"name":"container_01"}' },
  RESET_ANDROID: { desc: 'name：容器名称', example: '{"name":"container_01"}' },
  RENAME_ANDROID: { desc: 'name：原名称；newName：新名称', example: '{"name":"old_name","newName":"new_name"}' },
  SET_CLIPBOARD: { desc: 'name：容器名称；text：剪贴板内容', example: '{"name":"c1","text":"hello"}' },
  GET_CLIPBOARD: { desc: 'name：容器名称', example: '{"name":"c1"}' },
  SET_LANGUAGE: { desc: 'country：国家代码；language：语言代码', example: '{"name":"c1","country":"cn","language":"zh"}' },
  REFRESH_LOCATION: { desc: 'language：语言代码', example: '{"name":"c1","language":"zh"}' },
  SCREENSHOT: { desc: 'level：清晰度等级（1-3）', example: '{"name":"c1","level":"1"}' },
  SHELL_ADB: { desc: 'command：容器内 shell 命令（无需 adb 前缀）', example: '{"name":"c1","command":"pm list packages"}' }
}

/** 参数示例弹窗数据：操作分组拍平后附上示例 */
const paramHelpList = computed(() =>
  operationGroups.flatMap(g => g.options.map(op => ({
    label: op.label,
    example: (OPERATION_PARAM_EXAMPLES[op.value] || {}).example || '{}',
    desc: (OPERATION_PARAM_EXAMPLES[op.value] || {}).desc || ''
  })))
)

/** 操作参数输入框动态提示：跟随当前选中的执行操作 */
const paramPlaceholder = (operationCode) => {
  const item = OPERATION_PARAM_EXAMPLES[operationCode]
  if (!item) return '请先选择执行操作，再按示例填写 JSON 参数'
  return `示例：${item.example}\n说明：${item.desc}`
}

const form = reactive({
  name: '',
  metricType: 'CPU',
  metricCode: '',
  conditionType: 'NUMERIC',
  compareOp: 'GT',
  thresholdValue: 80,
  thresholdText: '',
  thresholdOptions: [],
  triggerMode: 'DURATION',
  durationSec: 60,
  consecutiveCount: 2,
  scopeType: 'ALL',
  scopeId: null,
  scopeIds: [],
  scopeAndroidNames: [],
  actions: []
})

const selectedCatalog = computed(() => metricCatalogs.value.find(item => item.code === form.metricCode))
const selectedValueType = computed(() => (selectedCatalog.value || {}).valueType || 'NUMBER')

const loadMetricCatalogs = async () => {
  const [host, android] = await Promise.all([metricCatalogApi.list({ targetType: 'HOST' }), metricCatalogApi.list({ targetType: 'ANDROID_INSTANCE' })])
  metricCatalogs.value = [...(host.data || []), ...(android.data || [])].filter(item => item.thresholdEnabled === 1)
}

const loadEnumOptions = async () => {
  enumOptions.value = []
  if (selectedValueType.value !== 'ENUM') return
  const res = await thresholdApi.metricOptions(form.metricCode)
  enumOptions.value = res.data || []
}

const onMetricChange = async () => {
  const valueType = selectedValueType.value
  form.conditionType = valueType === 'ENUM' ? 'ENUM' : (valueType === 'STRING' ? 'STRING' : 'NUMERIC')
  form.compareOp = valueType === 'ENUM' ? 'IN' : 'GT'
  form.thresholdOptions = []
  await loadEnumOptions()
}

/** 实例名称多选的数据源：作用范围内主机采集到过的安卓实例名 */
const androidNameOptions = ref([])
const androidNamesLoading = ref(false)

/** 按当前作用范围加载安卓实例名称列表 */
const loadAndroidNames = async () => {
  if (form.metricType !== 'ANDROID_STATUS') return
  if (form.scopeType === 'GROUP' && !form.scopeId) return
  if (form.scopeType === 'DEVICE' && form.scopeIds.length === 0) return
  androidNamesLoading.value = true
  try {
    const params = { scopeType: form.scopeType }
    if (form.scopeType === 'GROUP') params.scopeId = form.scopeId
    if (form.scopeType === 'DEVICE') params.scopeIds = form.scopeIds.join(',')
    const res = await deviceApi.androidNames(params)
    androidNameOptions.value = res.data || []
  } catch (e) {
    // 错误已在拦截器中提示，实例名仍可手动输入
  } finally {
    androidNamesLoading.value = false
  }
}

// 作用范围变化时联动刷新实例名称候选
watch([() => form.scopeType, () => form.scopeId, () => form.scopeIds.join(',')], loadAndroidNames)

// 指标类型切换时联动条件类型与默认比较操作
watch(() => form.metricType, (type) => {
  if (STRING_METRIC_TYPES.includes(type)) {
    form.conditionType = 'STRING'
    if (!['EQ', 'NE', 'CONTAINS'].includes(form.compareOp)) form.compareOp = 'EQ'
    loadAndroidNames()
  } else if (STATE_METRIC_TYPES.includes(type)) {
    form.conditionType = 'NONE'
    if (form.compareOp === 'CONTAINS') form.compareOp = 'GTE'
  } else {
    form.conditionType = 'NUMERIC'
    if (form.compareOp === 'CONTAINS') form.compareOp = 'GT'
  }
})

/**
 * 执行操作变更：容器/实例级操作在参数为空时默认填充 {"name":"${name}"} 占位符，
 * 阈值自动触发时后端会将其替换为触发告警的实例名；主机级操作无需参数
 */
const onOperationCodeChange = (action) => {
  const params = (action.operationParams || '').trim()
  if (action.operationCode && action.operationCode !== 'REBOOT_HOST' && (params === '' || params === '{}')) {
    action.operationParams = '{"name":"${name}"}'
  } else if (action.operationCode === 'REBOOT_HOST' && params === '{"name":"${name}"}') {
    action.operationParams = '{}'
  }
}

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
    case 'ANDROID_ONLINE':
    case 'ANDROID_OFFLINE':
      return '个'
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

/** 解析规则的多设备 ID：优先 scopeIds 逗号串，回退单个 scopeId */
const parseScopeIds = (rule) => {
  if (rule.scopeIds) {
    return rule.scopeIds.split(',').map(s => Number(s.trim())).filter(n => !isNaN(n))
  }
  return rule.scopeId ? [rule.scopeId] : []
}

/** 解析规则的多实例名：scopeAndroidName 逗号串拆为数组 */
const parseScopeAndroidNames = (rule) => {
  if (!rule.scopeAndroidName) return []
  return rule.scopeAndroidName.split(',').map(s => s.trim()).filter(Boolean)
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
      metricCode: toThresholdForm(rule).metricCode,
      conditionType: rule.conditionType || 'NUMERIC',
      compareOp: rule.compareOp || 'GT',
      thresholdValue: rule.thresholdValue != null ? Number(rule.thresholdValue) : 80,
      thresholdText: rule.thresholdText || '',
      triggerMode: rule.triggerMode || 'DURATION',
      durationSec: toThresholdForm(rule).durationSec,
      thresholdOptions: toThresholdForm(rule).thresholdOptions,
      consecutiveCount: rule.consecutiveCount || 2,
      scopeType: rule.scopeType || 'ALL',
      scopeId: rule.scopeId || null,
      scopeIds: parseScopeIds(rule),
      scopeAndroidNames: parseScopeAndroidNames(rule)
    })
    await loadEnumOptions()
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
  if (form.conditionType === 'NUMERIC' && (form.thresholdValue === null || form.thresholdValue === undefined || form.thresholdValue === '')) {
    ElMessage.warning('请输入阈值')
    return false
  }
  if (form.conditionType === 'STRING' && !form.thresholdText.trim()) {
    ElMessage.warning('请输入字符判断的目标文本')
    return false
  }
  if (form.scopeType === 'GROUP' && !form.scopeId) {
    ElMessage.warning('请选择分组')
    return false
  }
  if (form.scopeType === 'DEVICE' && form.scopeIds.length === 0) {
    ElMessage.warning('请选择至少一台设备')
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
      metricCode: form.metricCode,
      conditionType: form.conditionType,
      compareOp: form.conditionType === 'NONE' ? null : form.compareOp,
      thresholdValue: form.conditionType === 'NUMERIC' ? form.thresholdValue : null,
      thresholdText: form.conditionType === 'STRING' ? form.thresholdText.trim() : null,
      thresholdOptions: selectedValueType.value === 'ENUM' ? JSON.stringify(form.thresholdOptions) : null,
      triggerMode: form.triggerMode,
      durationSec: form.durationSec,
      consecutiveCount: form.consecutiveCount,
      scopeType: form.scopeType,
      // scopeId 保留首选项用于兼容列表展示，多选以 scopeIds 为准
      scopeId: form.scopeType === 'GROUP' ? form.scopeId
        : (form.scopeType === 'DEVICE' ? (form.scopeIds[0] || null) : null),
      scopeIds: form.scopeType === 'DEVICE' ? form.scopeIds : null,
      scopeAndroidName: form.metricType === 'ANDROID_STATUS' && form.scopeAndroidNames.length > 0
        ? form.scopeAndroidNames.join(',') : null,
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

onMounted(async () => {
  await Promise.all([loadGroups(), loadDevices(), loadMetricCatalogs()])
  if (isEdit.value) {
    await loadDetail()
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
.condition-block {
  width: 100%;
}
.condition-sub-row {
  margin-top: 8px;
}
.param-example {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}
.param-help-tip {
  margin-bottom: var(--spacing-md);
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
.hint-block {
  margin-left: 0;
  margin-top: 4px;
  line-height: 1.5;
}
.android-name-field {
  width: 100%;
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
