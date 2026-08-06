<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">{{ isEdit ? '编辑指标模板' : '新建指标模板' }}</h2>
      <el-button @click="$router.push('/metric-templates')">返回列表</el-button>
    </div>

    <div class="content-card" v-loading="loading">
      <el-form :model="form" label-width="100px" class="template-form">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="例如：安卓实例基础监控" />
        </el-form-item>
        <el-form-item label="目标类型" required>
          <el-radio-group v-model="form.targetType" :disabled="isEdit" @change="changeTargetType">
            <el-radio-button label="HOST">主机</el-radio-button>
            <el-radio-button label="ANDROID_INSTANCE">安卓实例</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模板状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>

      <div class="item-header">
        <h3>模板指标</h3>
        <span>选择指标后可分别设置采集频率与启用状态</span>
      </div>
      <el-table
        ref="catalogTableRef"
        :data="catalogs"
        row-key="id"
        v-loading="catalogLoading"
        @selection-change="updateSelection"
      >
        <el-table-column type="selection" width="52" reserve-selection />
        <el-table-column prop="name" label="指标名称" min-width="160">
          <template #default="{ row }">
            <div>{{ row.name }}</div>
            <small class="metric-code">{{ row.code }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column label="类型" width="110"><template #default="{ row }">{{ valueTypeLabel(row.valueType) }}</template></el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch v-if="isSelected(row.id)" v-model="itemConfig[row.id].enabled" :active-value="1" :inactive-value="0" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="采集频率（秒）" width="190">
          <template #default="{ row }">
            <el-input-number v-if="isSelected(row.id)" v-model="itemConfig[row.id].intervalSec" :min="15" :max="86400" :step="15" controls-position="right" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="枚举可选值" min-width="230">
          <template #default="{ row }">
            <el-select
              v-if="isSelected(row.id) && row.valueType === 'ENUM'"
              v-model="itemConfig[row.id].enumOptions"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入后回车添加"
              style="width: 100%"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="form-actions">
        <el-button @click="$router.push('/metric-templates')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存模板</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { metricCatalogApi, metricTemplateApi } from '@/api'
import { normalizeTemplateItem, parseEnumOptions } from '@/utils/metric-template'

const route = useRoute()
const router = useRouter()
const templateId = computed(() => route.params.id)
const isEdit = computed(() => Boolean(templateId.value))
const catalogTableRef = ref()
const catalogs = ref([])
const selectedIds = ref([])
const catalogLoading = ref(false)
const loading = ref(false)
const saving = ref(false)
const form = reactive({ name: '', targetType: 'HOST', enabled: 1 })
const itemConfig = reactive({})

const VALUE_TYPE_LABELS = { NUMBER: '数值', STRING: '字符串', ENUM: '枚举' }
const valueTypeLabel = (valueType) => VALUE_TYPE_LABELS[valueType] || valueType
const isSelected = (catalogId) => selectedIds.value.includes(catalogId)

function ensureItemConfig(catalog) {
  if (!itemConfig[catalog.id]) {
    itemConfig[catalog.id] = {
      valueType: catalog.valueType,
      enabled: 1,
      intervalSec: 60,
      enumOptions: []
    }
  }
}

function updateSelection(rows) {
  rows.forEach(ensureItemConfig)
  selectedIds.value = rows.map(row => row.id)
}

async function loadCatalogs() {
  catalogLoading.value = true
  try {
    const response = await metricCatalogApi.list({ targetType: form.targetType })
    catalogs.value = response.data || []
    await nextTick()
    catalogs.value.filter(row => selectedIds.value.includes(row.id)).forEach(row => catalogTableRef.value.toggleRowSelection(row, true))
  } finally {
    catalogLoading.value = false
  }
}

async function changeTargetType() {
  selectedIds.value = []
  Object.keys(itemConfig).forEach(key => delete itemConfig[key])
  await loadCatalogs()
}

async function loadDetail() {
  loading.value = true
  try {
    const response = await metricTemplateApi.detail(templateId.value)
    const data = response.data
    Object.assign(form, data.template)
    ;(data.items || []).forEach(item => {
      itemConfig[item.metricCatalogId] = {
        valueType: '',
        enabled: item.enabled,
        intervalSec: item.defaultIntervalSec,
        enumOptions: parseEnumOptions(item.enumOptions)
      }
    })
    selectedIds.value = (data.items || []).map(item => item.metricCatalogId)
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  const catalogsById = new Map(catalogs.value.map(catalog => [catalog.id, catalog]))
  const items = selectedIds.value.map(metricCatalogId => {
    const catalog = catalogsById.get(metricCatalogId)
    const config = itemConfig[metricCatalogId]
    const normalized = normalizeTemplateItem({
      valueType: catalog.valueType,
      intervalSec: config.intervalSec,
      enumOptions: config.enumOptions
    })
    return {
      metricCatalogId,
      enabled: config.enabled,
      defaultIntervalSec: normalized.intervalSec,
      enumOptions: JSON.stringify(normalized.enumOptions)
    }
  })
  return { name: form.name.trim(), targetType: form.targetType, enabled: form.enabled, items }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!selectedIds.value.length) {
    ElMessage.warning('请至少选择一个指标')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) await metricTemplateApi.update(templateId.value, payload)
    else await metricTemplateApi.create(payload)
    ElMessage.success('模板已保存')
    router.push('/metric-templates')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) await loadDetail()
  await loadCatalogs()
})
</script>

<style scoped>
.template-form {
  max-width: 680px;
}
.item-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 24px 0 12px;
}
.item-header h3 {
  font-size: 16px;
}
.item-header span,
.metric-code {
  color: var(--text-secondary);
  font-size: 12px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
