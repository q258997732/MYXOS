<template>
  <el-drawer v-model="visible" title="应用指标" size="720px" destroy-on-close>
    <el-steps :active="step" finish-status="success" simple>
      <el-step title="选择目标" /><el-step title="配置指标" /><el-step title="确认应用" />
    </el-steps>

    <section v-if="step === 0" class="drawer-section">
      <el-radio-group v-model="targetType" @change="resetTargets">
        <el-radio-button label="HOST">主机</el-radio-button>
        <el-radio-button label="ANDROID_INSTANCE">安卓实例</el-radio-button>
      </el-radio-group>
      <el-alert v-if="targetType === 'ANDROID_INSTANCE' && androidLoading" title="正在加载安卓实例，无法获取的设备将被跳过" type="info" :closable="false" class="drawer-alert" />
      <el-checkbox-group v-if="targetType === 'HOST'" v-model="hostIds" class="target-list">
        <el-checkbox v-for="device in devices" :key="device.id" :label="device.id">{{ device.name || device.ip }}</el-checkbox>
      </el-checkbox-group>
      <div v-else class="target-list">
        <div v-for="group in androidGroups" :key="group.deviceId" class="android-group">
          <strong>{{ group.deviceName }}</strong>
          <el-checkbox-group v-model="androidNames[group.deviceId]">
            <el-checkbox v-for="instance in group.instances" :key="instance.name" :label="instance.name">{{ instance.name }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </section>

    <section v-else-if="step === 1" class="drawer-section">
      <el-select v-model="category" placeholder="全部分类" clearable class="category-select">
        <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
      </el-select>
      <el-table :data="filteredCatalogs" size="small">
        <el-table-column prop="name" label="指标" min-width="180"><template #default="{ row }">{{ row.name }}<small class="metric-code">{{ row.code }}</small></template></el-table-column>
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch v-model="items[row.code].enabled" :active-value="1" :inactive-value="0" /></template></el-table-column>
        <el-table-column label="频率（秒）" width="150"><template #default="{ row }"><el-input-number v-model="items[row.code].intervalSec" :min="10" :disabled="items[row.code].enabled !== 1" /></template></el-table-column>
      </el-table>
      <div v-if="hasAppProcess" class="app-package-list">
        <el-divider>应用进程包名</el-divider>
        <el-form label-width="150px">
          <el-form-item v-for="target in targets" :key="targetKey(target)" :label="targetLabel(target)">
            <el-input v-model="appPackages[targetKey(target)]" placeholder="com.example.app" />
          </el-form-item>
        </el-form>
      </div>
    </section>

    <section v-else class="drawer-section">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="目标类型">{{ targetType === 'HOST' ? '主机' : '安卓实例' }}</el-descriptions-item>
        <el-descriptions-item label="应用目标"><el-tag v-for="target in targets" :key="targetKey(target)" class="target-tag">{{ targetLabel(target) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="指标">{{ enabledItems.map(item => item.metricCode).join('、') || '无' }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <template #footer>
      <el-button v-if="step > 0" @click="step--">上一步</el-button>
      <el-button v-if="step < 2" type="primary" @click="next">下一步</el-button>
      <el-button v-else type="primary" :loading="saving" @click="save">确认应用</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi, metricBindingApi, metricCatalogApi } from '@/api'
import { buildBatchMetricBindingPayload, groupAndroidInstances } from '@/utils/metric-application'

const props = defineProps({ modelValue: Boolean, devices: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const step = ref(0); const targetType = ref('HOST'); const hostIds = ref([]); const androidNames = reactive({}); const androidDevices = ref([])
const catalogs = ref([]); const items = reactive({}); const appPackages = reactive({}); const category = ref(''); const saving = ref(false); const androidLoading = ref(false)
const categories = computed(() => [...new Set(catalogs.value.map(item => item.category).filter(Boolean))])
const filteredCatalogs = computed(() => catalogs.value.filter(item => item.targetType === targetType.value && (!category.value || item.category === category.value)))
const androidGroups = computed(() => groupAndroidInstances(androidDevices.value))
const targets = computed(() => targetType.value === 'HOST'
  ? props.devices.filter(device => hostIds.value.includes(device.id)).map(device => ({ deviceId: device.id, deviceName: device.name || device.ip }))
  : androidGroups.value.flatMap(group => (androidNames[group.deviceId] || []).map(androidName => ({ deviceId: group.deviceId, deviceName: group.deviceName, androidName }))))
const enabledItems = computed(() => Object.values(items).filter(item => item.enabled === 1))
const hasAppProcess = computed(() => targetType.value === 'ANDROID_INSTANCE' && items.APP_PROCESS_STATE && items.APP_PROCESS_STATE.enabled === 1)
const targetKey = target => `${target.deviceId}:${target.androidName || ''}`
const targetLabel = target => target.androidName ? `${target.deviceName} / ${target.androidName}` : target.deviceName

async function loadCatalogs() {
  const response = await metricCatalogApi.list()
  catalogs.value = response.data || []
  catalogs.value.forEach(row => { if (!items[row.code]) items[row.code] = { metricCode: row.code, enabled: 0, intervalSec: row.defaultIntervalSec || row.intervalSec || 60 } })
}
async function loadAndroids() {
  androidLoading.value = true
  try {
    const results = await Promise.all(props.devices.map(async device => ({ ...device, androids: (await deviceApi.androids(device.id)).data || [] })))
    androidDevices.value = results
  } finally { androidLoading.value = false }
}
function resetTargets() { hostIds.value = []; Object.keys(androidNames).forEach(key => delete androidNames[key]) }
function next() {
  if (step.value === 0 && !targets.value.length) return ElMessage.warning('请选择至少一个应用目标')
  if (step.value === 1) {
    if (!enabledItems.value.length) return ElMessage.warning('请选择至少一个指标')
    if (hasAppProcess.value && targets.value.some(target => !(appPackages[targetKey(target)] || '').trim())) return ElMessage.warning('请为每个安卓实例填写应用包名')
  }
  step.value++
}
async function save() {
  saving.value = true
  try { await metricBindingApi.batch(buildBatchMetricBindingPayload({ targetType: targetType.value, targets: targets.value, items: enabledItems.value, appPackages })); ElMessage.success('指标应用已提交'); emit('saved'); visible.value = false } finally { saving.value = false }
}
watch(visible, async opened => { if (!opened) return; step.value = 0; hostIds.value = props.devices.map(device => device.id); await loadCatalogs(); await loadAndroids() })
</script>

<style scoped>
.drawer-section { padding: 24px 0; }.drawer-alert { margin: 16px 0; }.target-list { display: grid; gap: 12px; margin-top: 20px; }.android-group { border-bottom: 1px solid #ebeef5; padding: 12px 0; }.android-group .el-checkbox-group { margin-top: 8px; }.category-select { margin-bottom: 12px; width: 180px; }.metric-code { display: block; color: #909399; }.app-package-list { margin-top: 20px; }.target-tag { margin: 2px; }
</style>
